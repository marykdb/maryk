package maryk.datastore.remote

import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.URLParserException
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
    private val definitionsMutex = Mutex()

    override val dataModelIdsByString: Map<String, UInt> = dataModelsById.map { (id, model) ->
        model.Meta.name to id
    }.toMap()

    companion object {
        suspend fun connect(config: RemoteStoreConfig): RemoteDataStore {
            val client = config.httpClient ?: createDefaultHttpClient()
            val ownsClient = config.httpClient == null
            if (config.baseUrl != config.baseUrl.trim()) {
                throw IllegalArgumentException("Remote store base URL cannot contain leading or trailing whitespace.")
            }
            val trimmedBaseUrl = config.baseUrl.trim()
            if (trimmedBaseUrl.isEmpty()) {
                throw IllegalArgumentException("Remote store base URL cannot be blank.")
            }
            val baseUrl = try {
                Url(trimmedBaseUrl)
            } catch (error: URLParserException) {
                throw IllegalArgumentException("Remote store base URL is invalid: `${config.baseUrl}`", error)
            }
            validateBaseUrl(baseUrl)
            if (baseUrl.protocol != URLProtocol.HTTP) {
                throw IllegalArgumentException("Remote store only supports http URLs.")
            }
            if (baseUrl.port !in 1..65535) {
                throw IllegalArgumentException("Remote store base URL port must be between 1 and 65535.")
            }
            val effectiveUrl: Url
            val tunnel: SshTunnel?
            if (config.ssh != null) {
                validateSshConfig(config.ssh)
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

        private fun validateSshConfig(config: RemoteSshConfig) {
            if (config.host.isBlank()) {
                throw IllegalArgumentException("SSH host cannot be blank.")
            }
            if (config.port !in 1..65535) {
                throw IllegalArgumentException("SSH port must be between 1 and 65535.")
            }
            if (config.localPort != null && config.localPort !in 1..65535) {
                throw IllegalArgumentException("SSH local port must be between 1 and 65535.")
            }
            if (config.remotePort != null && config.remotePort !in 1..65535) {
                throw IllegalArgumentException("SSH remote port must be between 1 and 65535.")
            }
            if (config.user != null && config.user.isBlank()) {
                throw IllegalArgumentException("SSH user cannot be blank.")
            }
            if (config.remoteHost != null && config.remoteHost.isBlank()) {
                throw IllegalArgumentException("SSH remote host cannot be blank.")
            }
            if (config.identityFile != null && config.identityFile.isBlank()) {
                throw IllegalArgumentException("SSH identity file cannot be blank.")
            }
            if (config.extraArgs.any { it.isBlank() }) {
                throw IllegalArgumentException("SSH extra arguments cannot contain blank values.")
            }
        }

        private fun resolveSshTarget(baseUrl: Url, config: RemoteSshConfig): SshTarget {
            val host = config.remoteHost ?: baseUrl.host
            val port = config.remotePort ?: baseUrl.port
            if (host.isBlank()) {
                throw IllegalArgumentException("SSH target host cannot be blank.")
            }
            if (port !in 1..65535) {
                throw IllegalArgumentException("SSH target port must be between 1 and 65535.")
            }
            return SshTarget(host = host, port = port)
        }

        private suspend fun fetchInfo(client: HttpClient, baseUrl: Url): InfoResult {
            val response = client.get(buildUrl(baseUrl, RemoteStoreProtocol.infoPath)) {
                headers {
                    append(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                }
            }
            requireSuccess(response, "info")
            requireContentType(response, RemoteStoreProtocol.contentType, "info")
            val bytes = response.body<ByteArray>()
            if (bytes.isEmpty()) {
                throw IllegalStateException("Remote store info returned an empty payload")
            }
            val definitionsContext = DefinitionsConversionContext()
            val info = RemoteStoreCodec.decode(RemoteStoreInfo.Serializer, bytes, definitionsContext)
            return InfoResult(info, definitionsContext)
        }

        private fun buildModelMap(
            info: RemoteStoreInfo,
            definitionsContext: ContainsDefinitionsContext,
        ): Map<UInt, IsRootDataModel> {
            val duplicateIds = info.modelIds.groupBy { it.id }.filterValues { it.size > 1 }.keys
            if (duplicateIds.isNotEmpty()) {
                throw IllegalStateException("Duplicate model id(s) in remote info: ${duplicateIds.joinToString(", ")}")
            }
            val duplicateNames = info.modelIds.groupBy { it.name }.filterValues { it.size > 1 }.keys
            if (duplicateNames.isNotEmpty()) {
                throw IllegalStateException("Duplicate model name(s) in remote info: ${duplicateNames.joinToString(", ")}")
            }
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
            Url(baseUrl.toString().substringBefore('?').substringBefore('#').trimEnd('/') + path)

        private fun validateBaseUrl(baseUrl: Url) {
            if (baseUrl.host.isBlank()) {
                throw IllegalArgumentException("Remote store base URL requires a host.")
            }
            if (baseUrl.parameters.names().isNotEmpty()) {
                throw IllegalArgumentException("Remote store base URL cannot contain query parameters.")
            }
            if (baseUrl.fragment.isNotEmpty()) {
                throw IllegalArgumentException("Remote store base URL cannot contain a fragment.")
            }
            if (!baseUrl.user.isNullOrEmpty() || !baseUrl.password.isNullOrEmpty()) {
                throw IllegalArgumentException("Remote store base URL cannot contain user info.")
            }
        }

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
        requireContentType(response, RemoteStoreProtocol.contentType, "execute")
        val responseBytes = response.body<ByteArray>()
        if (responseBytes.isEmpty()) {
            throw IllegalStateException("Remote store execute returned an empty payload")
        }

        val lengthResult = RemoteStoreCodec.readLengthPrefix(responseBytes, 0)
            ?: throw IllegalStateException("Missing response length prefix")
        if (lengthResult.length < 0) {
            throw IllegalStateException("Invalid response length prefix: ${lengthResult.length}")
        }
        if (lengthResult.length == 0) {
            throw IllegalStateException("Invalid response length prefix: 0")
        }
        if (lengthResult.length > MAX_FRAME_SIZE_BYTES) {
            throw IllegalStateException("Response frame exceeds max size: ${lengthResult.length} > $MAX_FRAME_SIZE_BYTES")
        }
        val endIndex = lengthResult.nextOffset + lengthResult.length
        if (endIndex > responseBytes.size) {
            throw IllegalStateException("Response payload truncated")
        }
        if (endIndex != responseBytes.size) {
            throw IllegalStateException("Response contains trailing bytes after payload")
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
                    requireContentType(response, RemoteStoreProtocol.streamContentType, "flow")
                    val channel = response.bodyAsChannel()
                    val handle = RemoteFlowHandle(response, channel)
                    listeners.add(handle)

                    try {
                        val lengthBuffer = ByteArray(4)
                        while (!channel.isClosedForRead) {
                            val lengthResult = readFrameLength(channel, lengthBuffer) ?: break
                            if (lengthResult.length < 0) {
                                throw IllegalStateException("Invalid streamed response length prefix: ${lengthResult.length}")
                            }
                            if (lengthResult.length == 0) {
                                throw IllegalStateException("Invalid streamed response length prefix: 0")
                            }
                            if (lengthResult.length > MAX_FRAME_SIZE_BYTES) {
                                throw IllegalStateException("Streamed response frame exceeds max size: ${lengthResult.length} > $MAX_FRAME_SIZE_BYTES")
                            }
                            val messageBytes = ByteArray(lengthResult.length)
                            readFramePayload(channel, messageBytes)
                            val responseContext = RequestContext(definitionsContext, dataModel = request.dataModel)
                            val updatesResponse = RemoteStoreCodec.decode(UpdatesResponse.Serializer, messageBytes, responseContext)
                            if (updatesResponse.updates.isEmpty()) {
                                throw IllegalStateException("Remote store flow returned empty update frame")
                            }
                            if (updatesResponse.dataModel.Meta.name != request.dataModel.Meta.name) {
                                throw IllegalStateException(
                                    "Remote store flow data model mismatch: expected `${request.dataModel.Meta.name}` but got `${updatesResponse.dataModel.Meta.name}`"
                                )
                            }
                            updatesResponse.updates.forEach { update ->
                                @Suppress("UNCHECKED_CAST")
                                val sendResult = trySend(update as IsUpdateResponse<DM>)
                                if (sendResult.isFailure) {
                                    return@execute
                                }
                            }
                        }
                    } finally {
                        handle.close()
                        listeners.remove(handle)
                    }
                }
            }

            job.invokeOnCompletion { cause ->
                close(cause)
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
        requireContentType(response, RemoteStoreProtocol.contentType, "process-update")
        val responseBytes = response.body<ByteArray>()
        if (responseBytes.isEmpty()) {
            throw IllegalStateException("Remote store process-update returned an empty payload")
        }

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

    private suspend fun ensureDataModelReference(model: IsRootDataModel) {
        definitionsMutex.withLock {
            val existing = definitionsContext.dataModels[model.Meta.name]
            if (existing == null || existing.get() !== model) {
                definitionsContext.dataModels[model.Meta.name] = DataModelReference(model)
            }
        }
    }
}

private class RemoteListenerRegistry {
    private val mutex = Mutex()
    private val listeners = mutableSetOf<RemoteFlowHandle>()

    suspend fun add(handle: RemoteFlowHandle) = mutex.withLock { listeners.add(handle) }

    suspend fun remove(handle: RemoteFlowHandle) = mutex.withLock { listeners.remove(handle) }

    suspend fun closeAll() {
        val snapshot = mutex.withLock {
            val handles = listeners.toList()
            listeners.clear()
            handles
        }
        snapshot.forEach { it.close() }
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

private suspend fun requireSuccess(response: HttpResponse, operation: String) {
    if (response.status.value !in 200..299) {
        val bodyPreview = runCatching { response.bodyAsText() }
            .getOrNull()
            ?.replace(Regex("\\s+"), " ")
            ?.take(200)
        val suffix = if (bodyPreview.isNullOrBlank()) "" else ": $bodyPreview"
        throw IllegalStateException(
            "Remote store $operation failed with HTTP ${response.status.value} ${response.status.description}$suffix"
        )
    }
}

private fun requireContentType(response: HttpResponse, expected: String, operation: String) {
    val actual = response.headers[HttpHeaders.ContentType]
        ?.substringBefore(';')
        ?.trim()
        ?: throw IllegalStateException("Remote store $operation missing Content-Type header")
    if (!actual.equals(expected, ignoreCase = true)) {
        throw IllegalStateException("Remote store $operation returned unexpected Content-Type `$actual` (expected `$expected`)")
    }
}

private const val MAX_FRAME_SIZE_BYTES = 16 * 1024 * 1024

private suspend fun readFrameLength(
    channel: ByteReadChannel,
    lengthBuffer: ByteArray,
): RemoteStoreCodec.LengthResult? {
    val read = channel.readAvailable(lengthBuffer, 0, lengthBuffer.size)
    if (read == -1) return null
    if (read < lengthBuffer.size) {
        runCatching {
            channel.readFully(lengthBuffer, read, lengthBuffer.size - read)
        }.getOrElse {
            throw IllegalStateException("Stream ended while reading frame length prefix", it)
        }
    }
    return RemoteStoreCodec.readLengthPrefix(lengthBuffer, 0)
}

private suspend fun readFramePayload(
    channel: ByteReadChannel,
    payload: ByteArray,
) {
    runCatching {
        channel.readFully(payload, 0, payload.size)
    }.getOrElse {
        throw IllegalStateException("Stream ended while reading frame payload", it)
    }
}
