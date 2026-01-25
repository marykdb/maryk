package maryk.datastore.remote

import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readFully
import io.ktor.client.statement.bodyAsChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maryk.core.models.IsRootDataModel
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.RequestContext
import maryk.core.query.requests.IsFetchRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.IsTransportableRequest
import maryk.core.query.requests.Requests
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.responses.IsDataModelResponse
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.datastore.shared.IsDataStore
import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import io.ktor.client.statement.HttpResponse

class RemoteDataStore private constructor(
    private val httpClient: HttpClient,
    private val baseUrl: Url,
    private val definitionsContext: ContainsDefinitionsContext,
    private val listeners: RemoteListenerRegistry,
    private val sshTunnel: SshTunnel?,
    private val ownsClient: Boolean,
    override val dataModelsById: Map<UInt, IsRootDataModel>,
    override val keepAllVersions: Boolean,
    override val supportsFuzzyQualifierFiltering: Boolean,
    override val supportsSubReferenceFiltering: Boolean,
) : IsDataStore {
    override val dataModelIdsByString: Map<String, UInt> = dataModelsById.map { (id, model) ->
        model.Meta.name to id
    }.toMap()

    companion object {
        suspend fun connect(config: RemoteStoreConfig): RemoteDataStore {
            val client = config.httpClient ?: createDefaultHttpClient()
            val ownsClient = config.httpClient == null
            val baseUrl = Url(config.baseUrl)
            if (baseUrl.protocol.name != "http") {
                throw IllegalArgumentException("Remote store only supports http URLs.")
            }
            val effectiveUrl: Url
            val tunnel: SshTunnel?
            if (config.ssh != null) {
                val target = resolveSshTarget(baseUrl, config.ssh)
                val factory = config.sshTunnelFactory
                    ?: throw IllegalArgumentException("SSH tunnel factory is not available on this platform")
                tunnel = factory.open(config.ssh, target)
                effectiveUrl = URLBuilder(baseUrl).apply {
                    host = "127.0.0.1"
                    port = tunnel.localPort
                }.build()
            } else {
                effectiveUrl = baseUrl
                tunnel = null
            }

            return try {
                val infoResult = fetchInfo(client, effectiveUrl)
                val modelMap = buildModelMap(infoResult.info, infoResult.definitionsContext)

                RemoteDataStore(
                    httpClient = client,
                    baseUrl = effectiveUrl,
                    definitionsContext = infoResult.definitionsContext,
                    listeners = RemoteListenerRegistry(),
                    sshTunnel = tunnel,
                    ownsClient = ownsClient,
                    dataModelsById = modelMap,
                    keepAllVersions = infoResult.info.keepAllVersions,
                    supportsFuzzyQualifierFiltering = infoResult.info.supportsFuzzyQualifierFiltering,
                    supportsSubReferenceFiltering = infoResult.info.supportsSubReferenceFiltering,
                )
            } catch (error: Throwable) {
                if (ownsClient) {
                    client.close()
                }
                tunnel?.close()
                throw error
            }
        }

        private fun resolveSshTarget(baseUrl: Url, config: RemoteSshConfig): SshTarget {
            val host = config.remoteHost ?: baseUrl.host
            val port = config.remotePort ?: baseUrl.port
            return SshTarget(host = host, port = port)
        }

        private suspend fun fetchInfo(client: HttpClient, baseUrl: Url): InfoResult {
            val response = client.get(buildUrl(baseUrl, RemoteStoreProtocol.infoPath)) {
                headers {
                    append(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                }
            }
            requireSuccess(response, "info")
            val bytes = response.body<ByteArray>()
            val definitionsContext = DefinitionsConversionContext()
            val info = RemoteStoreCodec.decode(RemoteStoreInfo.Serializer, bytes, definitionsContext)
            return InfoResult(info, definitionsContext)
        }

        private fun buildModelMap(
            info: RemoteStoreInfo,
            definitionsContext: ContainsDefinitionsContext,
        ): Map<UInt, IsRootDataModel> {
            val dataModels = definitionsContext.dataModels
            return info.modelIds.associate { entry ->
                val reference = dataModels[entry.name]
                    ?: throw IllegalStateException("Model ${entry.name} missing from definitions")
                val model = reference.get() as? IsRootDataModel
                    ?: throw IllegalStateException("Model ${entry.name} is not a root data model")
                entry.id to model
            }
        }

        private fun buildUrl(baseUrl: Url, path: String): Url =
            Url(baseUrl.toString().trimEnd('/') + path)

        internal fun collectDefinitions(models: Collection<IsRootDataModel>): Definitions {
            val seen = linkedSetOf<String>()
            val definitions = mutableListOf<MarykPrimitive>()
            val ordered = models.sortedBy { it.Meta.name }
            ordered.forEach { model ->
                val dependencies = mutableListOf<MarykPrimitive>()
                model.getAllDependencies(dependencies)
                dependencies.forEach { dependency ->
                    if (seen.add(dependency.Meta.name)) {
                        definitions.add(dependency)
                    }
                }
                if (seen.add(model.Meta.name)) {
                    definitions.add(model as MarykPrimitive)
                }
            }
            return Definitions(definitions)
        }
    }

    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ,
    ): RP {
        ensureDataModelReference(request.dataModel)
        val transportable = request as? IsTransportableRequest<*>
            ?: throw IllegalArgumentException("Request ${request::class.simpleName} is not transportable")
        val context = RequestContext(definitionsContext, dataModel = request.dataModel)
        val payload = RemoteStoreCodec.encode(Requests.Serializer, Requests(transportable), context)
        val response = httpClient.post(buildUrl(baseUrl, RemoteStoreProtocol.executePath)) {
            headers {
                append(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                append(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
            }
            setBody(payload)
        }
        requireSuccess(response, "execute")
        val responseBytes = response.body<ByteArray>()

        val lengthResult = RemoteStoreCodec.readLengthPrefix(responseBytes, 0)
            ?: throw IllegalStateException("Missing response length prefix")
        val endIndex = lengthResult.nextOffset + lengthResult.length
        if (endIndex > responseBytes.size) {
            throw IllegalStateException("Response payload truncated")
        }
        val payloadBytes = responseBytes.copyOfRange(lengthResult.nextOffset, endIndex)
        val responseContext = RequestContext(definitionsContext, dataModel = request.dataModel)
        @Suppress("UNCHECKED_CAST")
        return RemoteStoreCodec.decode(request.responseModel.Serializer, payloadBytes, responseContext) as RP
    }

    override suspend fun <DM : IsRootDataModel, RQ : IsFetchRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
        request: RQ,
    ): Flow<IsUpdateResponse<DM>> {
        ensureDataModelReference(request.dataModel)
        val transportable = request as? IsTransportableRequest<*>
            ?: throw IllegalArgumentException("Request ${request::class.simpleName} is not transportable")
        val context = RequestContext(definitionsContext, dataModel = request.dataModel)
        val payload = RemoteStoreCodec.encode(Requests.Serializer, Requests(transportable), context)

        return callbackFlow {
            val scope = this
            val statement = httpClient.preparePost(buildUrl(baseUrl, RemoteStoreProtocol.flowPath)) {
                headers {
                    append(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                    append(HttpHeaders.Accept, RemoteStoreProtocol.streamContentType)
                }
                setBody(payload)
            }

            val job = launch(Dispatchers.Default) {
                statement.execute { response ->
                    requireSuccess(response, "flow")
                    val channel = response.bodyAsChannel()
                    val handle = RemoteFlowHandle(response, channel)
                    listeners.add(handle)

                    try {
                        val lengthBuffer = ByteArray(4)
                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(lengthBuffer, 0, lengthBuffer.size)
                            if (read == -1) break
                            if (read < lengthBuffer.size) {
                                channel.readFully(lengthBuffer, read, lengthBuffer.size - read)
                            }
                            val lengthResult = RemoteStoreCodec.readLengthPrefix(lengthBuffer, 0) ?: break
                            if (lengthResult.length <= 0) continue
                            val messageBytes = ByteArray(lengthResult.length)
                            channel.readFully(messageBytes, 0, messageBytes.size)
                            val responseContext = RequestContext(definitionsContext, dataModel = request.dataModel)
                            val updatesResponse = RemoteStoreCodec.decode(UpdatesResponse.Serializer, messageBytes, responseContext)
                            updatesResponse.updates.forEach { update ->
                                @Suppress("UNCHECKED_CAST")
                                send(update as IsUpdateResponse<DM>)
                            }
                        }
                    } finally {
                        handle.close()
                        scope.launch {
                            listeners.remove(handle)
                        }
                    }
                }
            }

            awaitClose {
                job.cancel()
            }
        }
    }

    override suspend fun <DM : IsRootDataModel> processUpdate(
        updateResponse: UpdateResponse<DM>,
    ): ProcessResponse<DM> {
        ensureDataModelReference(updateResponse.dataModel)
        val context = RequestContext(definitionsContext, dataModel = updateResponse.dataModel)
        val payload = RemoteStoreCodec.encode(UpdateResponse.Serializer, updateResponse, context)
        val response = httpClient.post(buildUrl(baseUrl, RemoteStoreProtocol.processUpdatePath)) {
            headers {
                append(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                append(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
            }
            setBody(payload)
        }
        requireSuccess(response, "process-update")
        val responseBytes = response.body<ByteArray>()

        val responseContext = RequestContext(definitionsContext, dataModel = updateResponse.dataModel)
        val remoteResponse = RemoteStoreCodec.decode(RemoteProcessResponse.Serializer, responseBytes, responseContext)
        @Suppress("UNCHECKED_CAST")
        return ProcessResponse(remoteResponse.version, remoteResponse.result as IsDataModelResponse<DM>)
    }

    override suspend fun close() {
        listeners.closeAll()
        sshTunnel?.close()
        if (ownsClient) {
            httpClient.close()
        }
    }

    override suspend fun closeAllListeners() {
        listeners.closeAll()
    }

    private fun ensureDataModelReference(model: IsRootDataModel) {
        definitionsContext.dataModels[model.Meta.name] = DataModelReference(model)
    }
}

private class RemoteListenerRegistry {
    private val mutex = Mutex()
    private val listeners = mutableSetOf<RemoteFlowHandle>()

    suspend fun add(handle: RemoteFlowHandle) = mutex.withLock { listeners.add(handle) }

    suspend fun remove(handle: RemoteFlowHandle) = mutex.withLock { listeners.remove(handle) }

    suspend fun closeAll() {
        val snapshot = mutex.withLock { listeners.toList() }
        snapshot.forEach { it.close() }
        mutex.withLock { listeners.clear() }
    }
}

private data class RemoteFlowHandle(
    val response: HttpResponse,
    val channel: ByteReadChannel,
) {
    fun close() {
        channel.cancel(null)
        response.call.cancel()
    }
}

private data class InfoResult(
    val info: RemoteStoreInfo,
    val definitionsContext: ContainsDefinitionsContext,
)

private fun requireSuccess(response: HttpResponse, operation: String) {
    if (response.status.value !in 200..299) {
        throw IllegalStateException("Remote store $operation failed with HTTP ${response.status.value}")
    }
}
