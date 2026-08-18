package maryk.datastore.remote

import io.ktor.client.request.get
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HeadersBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.URLParserException
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationMetrics
import maryk.core.models.migration.MigrationRuntimeStatus
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.contextual.IsDataModelReference
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.RequestContext
import maryk.core.query.requests.CollectRequest
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.IsTransportableRequest
import maryk.core.query.requests.RequestType
import maryk.core.query.requests.Requests
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsDataModelResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.properties.types.TypedValue
import maryk.core.values.ObjectValues
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.SnapshotVersionProvider
import maryk.datastore.shared.migration.MigrationAdmin
import maryk.datastore.shared.migration.MigrationAdminSnapshot
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.runCatchingNonFatal

private data class BatchRequestDescriptor(
    val responseModel: IsTypedObjectDataModel<in IsResponse, *, RequestContext, RequestContext>,
    val dataModel: IsRootDataModel,
)

class RemoteDataStore private constructor(
    private val httpClient: HttpClient,
    private val baseUrl: Url,
    private val definitionsContext: ContainsDefinitionsContext,
    private val listeners: RemoteListenerRegistry,
    private val sshTunnel: SshTunnel?,
    private val ownsClient: Boolean,
    private val bearerToken: String?,
    private val flowRetryPolicy: RemoteFlowRetryPolicy,
    override val dataModelsById: Map<UInt, IsRootDataModel>,
    override val keepAllVersions: Boolean,
    override val keepUpdateHistoryIndex: Boolean,
    override val supportsFuzzyQualifierFiltering: Boolean,
    override val supportsSubReferenceFiltering: Boolean,
) : IsDataStore, MigrationAdmin, SnapshotVersionProvider {
    private val definitionsMutex = Mutex()
    private val localDataModelsByName = mutableMapOf<String, IsRootDataModel>()

    override val dataModelIdsByString: Map<String, UInt> = dataModelsById.map { (id, model) ->
        model.Meta.name to id
    }.toMap()

    companion object {
        suspend fun connect(config: RemoteStoreConfig): RemoteDataStore =
            connect(config, allowInsecureBearerTransport = false)

        suspend fun connect(
            config: RemoteStoreConfig,
            allowInsecureBearerTransport: Boolean,
        ): RemoteDataStore {
            if (config.bearerToken != null && config.bearerToken.isBlank()) {
                throw IllegalArgumentException("Remote store bearer token cannot be blank.")
            }
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
            if (baseUrl.protocol != URLProtocol.HTTP && baseUrl.protocol != URLProtocol.HTTPS) {
                throw IllegalArgumentException("Remote store only supports http or https URLs.")
            }
            if (baseUrl.port !in 1..65535) {
                throw IllegalArgumentException("Remote store base URL port must be between 1 and 65535.")
            }

            val client = config.httpClient ?: createDefaultHttpClient()
            val ownsClient = config.httpClient == null
            var tunnel: SshTunnel? = null
            return try {
                val effectiveUrl = if (config.ssh != null) {
                    validateSshConfig(config.ssh)
                    require(baseUrl.protocol != URLProtocol.HTTPS) {
                        "SSH tunneling over HTTPS is not supported because local forwarding cannot preserve TLS authority validation"
                    }
                    val target = resolveSshTarget(baseUrl, config.ssh)
                    val factory = config.sshTunnelFactory
                        ?: throw IllegalArgumentException("SSH tunnel factory is not available on this platform")
                    tunnel = factory.open(config.ssh, target)
                    URLBuilder(baseUrl).apply {
                        host = "127.0.0.1"
                        port = tunnel.localPort
                    }.build()
                } else {
                    baseUrl
                }
                if (
                    config.bearerToken != null &&
                    effectiveUrl.protocol == URLProtocol.HTTP &&
                    !effectiveUrl.host.isLoopbackIpLiteral() &&
                    !allowInsecureBearerTransport
                ) {
                    throw IllegalArgumentException(
                        "Remote store refuses a bearer token over public plaintext HTTP; " +
                            "use HTTPS, an SSH tunnel, or explicitly allow insecure bearer transport."
                    )
                }

                val infoResult = fetchInfo(client, effectiveUrl, config.bearerToken)
                val modelMap = buildModelMap(infoResult.info, infoResult.definitionsContext)

                RemoteDataStore(
                    httpClient = client,
                    baseUrl = effectiveUrl,
                    definitionsContext = infoResult.definitionsContext,
                    listeners = RemoteListenerRegistry(),
                    sshTunnel = tunnel,
                    ownsClient = ownsClient,
                    bearerToken = config.bearerToken,
                    flowRetryPolicy = config.flowRetryPolicy,
                    dataModelsById = modelMap,
                    keepAllVersions = infoResult.info.keepAllVersions,
                    keepUpdateHistoryIndex = infoResult.info.keepUpdateHistoryIndex,
                    supportsFuzzyQualifierFiltering = infoResult.info.supportsFuzzyQualifierFiltering,
                    supportsSubReferenceFiltering = infoResult.info.supportsSubReferenceFiltering,
                )
            } catch (error: Throwable) {
                if (ownsClient) {
                    try {
                        client.close()
                    } catch (cleanupError: Throwable) {
                        cleanupError.rethrowIfFatal()
                    }
                }
                try {
                    tunnel?.close()
                } catch (cleanupError: Throwable) {
                    cleanupError.rethrowIfFatal()
                }
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

        private suspend fun fetchInfo(client: HttpClient, baseUrl: Url, bearerToken: String?): InfoResult {
            val bytes = readNonFlowResponseBytes("info") {
                client.get(buildUrl(baseUrl, RemoteStoreProtocol.infoPath)) {
                    headers {
                        append(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                        appendBearerToken(bearerToken)
                    }
                }
            }
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
        val transportable = request as? IsTransportableRequest<*>
            ?: throw IllegalArgumentException("Request ${request::class.simpleName} is not transportable")
        val responses = execute(Requests(transportable))
        @Suppress("UNCHECKED_CAST")
        return responses.single() as RP
    }

    /** Execute an ordered request batch in one remote round trip. */
    suspend fun execute(requests: Requests): List<IsResponse> {
        require(requests.requests.isNotEmpty()) { "Remote execute request list cannot be empty" }
        val descriptors = requests.requests.map(::batchRequestDescriptor)
        registerLocalDataModels(descriptors.map { it.dataModel })

        val context = requestContext(descriptors.first().dataModel, descriptors.map { it.dataModel })
        val payload = RemoteStoreCodec.encode(Requests.Serializer, requests, context, MAX_REQUEST_BODY_BYTES)
        return executeEncodedBatch(payload, descriptors)
    }

    private suspend fun executeEncodedBatch(
        payload: ByteArray,
        descriptors: List<BatchRequestDescriptor>,
    ): List<IsResponse> {
        val responseBytes = readNonFlowResponseBytes("execute", MAX_BATCH_RESPONSE_BODY_BYTES) {
            httpClient.post(buildUrl(baseUrl, RemoteStoreProtocol.executePath)) {
                headers {
                    append(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                    append(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                    append(
                        RemoteStoreProtocol.executeProtocolHeader,
                        RemoteStoreProtocol.batchExecuteProtocol,
                    )
                    appendBearerToken(bearerToken)
                }
                setBody(payload)
            }
        }
        if (responseBytes.isEmpty()) {
            throw IllegalStateException("Remote store execute returned an empty payload")
        }

        return try {
            decodeFramedBatchResponse(responseBytes, descriptors)
        } catch (framingError: IllegalStateException) {
            if (descriptors.size != 1 || responseBytes.size > MAX_FRAME_SIZE_BYTES) {
                throw framingError
            }
            try {
                listOf(decodeResponse(responseBytes, descriptors.single()))
            } catch (_: IllegalStateException) {
                throw framingError
            }
        }
    }

    private fun decodeFramedBatchResponse(
        responseBytes: ByteArray,
        descriptors: List<BatchRequestDescriptor>,
    ): List<IsResponse> {
        val responses = ArrayList<IsResponse>(descriptors.size)
        var offset = 0
        descriptors.forEachIndexed { index, descriptor ->
            val lengthResult = RemoteStoreCodec.readLengthPrefix(responseBytes, offset)
                ?: throw IllegalStateException("Missing response length prefix for batch item $index")
            if (lengthResult.length < 0) {
                throw IllegalStateException("Invalid response length prefix: ${lengthResult.length}")
            }
            if (lengthResult.length == 0) {
                throw IllegalStateException("Invalid response length prefix: 0")
            }
            if (lengthResult.length > MAX_FRAME_SIZE_BYTES) {
                throw IllegalStateException(
                    "Response frame exceeds max size: ${lengthResult.length} > $MAX_FRAME_SIZE_BYTES"
                )
            }
            val endIndex = lengthResult.nextOffset + lengthResult.length
            if (endIndex > responseBytes.size) {
                throw IllegalStateException("Response payload truncated for batch item $index")
            }
            responses += decodeResponse(
                responseBytes.copyOfRange(lengthResult.nextOffset, endIndex),
                descriptor,
            )
            offset = endIndex
        }
        if (offset != responseBytes.size) {
            throw IllegalStateException("Response contains trailing bytes after payload")
        }
        return responses
    }

    private fun decodeResponse(
        payload: ByteArray,
        descriptor: BatchRequestDescriptor,
    ): IsResponse {
        val responseContext = requestContext(descriptor.dataModel)
        @Suppress("UNCHECKED_CAST")
        return RemoteStoreCodec.decode(
            descriptor.responseModel.Serializer,
            payload,
            responseContext,
        ) as IsResponse
    }

    /** Execute an ordered request batch containing unresolved Inject values. */
    suspend fun execute(requests: ObjectValues<Requests, Requests.Companion>): List<IsResponse> {
        @Suppress("UNCHECKED_CAST")
        val typedRequests = requests.original(Requests.requests.index)
            as? List<TypedValue<RequestType, Any>>
            ?: throw IllegalArgumentException("Remote execute request list cannot be empty")
        require(typedRequests.isNotEmpty()) { "Remote execute request list cannot be empty" }
        val descriptors = typedRequests.map { typed ->
            batchRequestDescriptor(typed.type, typed.value)
        }
        registerLocalDataModels(descriptors.map { it.dataModel })
        val context = requestContext(descriptors.first().dataModel, descriptors.map { it.dataModel })
        val payload = RemoteStoreCodec.encodeValues(
            Requests.Serializer,
            requests,
            context,
            MAX_REQUEST_BODY_BYTES,
        )
        return executeEncodedBatch(payload, descriptors)
    }

    private fun batchRequestDescriptor(request: IsTransportableRequest<*>): BatchRequestDescriptor {
        val executableRequest = if (request is CollectRequest<*, *>) request.request else request
        val storeRequest = executableRequest as? IsStoreRequest<*, *>
            ?: throw IllegalArgumentException(
                "Remote execute only accepts store requests or CollectRequest wrappers"
            )
        @Suppress("UNCHECKED_CAST")
        return BatchRequestDescriptor(
            executableRequest.responseModel as IsTypedObjectDataModel<in IsResponse, *, RequestContext, RequestContext>,
            storeRequest.dataModel,
        )
    }

    private fun batchRequestDescriptor(type: RequestType, value: Any): BatchRequestDescriptor {
        if (value is IsTransportableRequest<*>) {
            return batchRequestDescriptor(value)
        }
        val values = value as? ObjectValues<*, *>
            ?: throw IllegalArgumentException("Unsupported remote batch request value ${value::class.simpleName}")
        if (type == RequestType.Collect) {
            val collectRequest = values.toDataObject() as? IsTransportableRequest<*>
                ?: throw IllegalArgumentException("Invalid CollectRequest in remote batch")
            return batchRequestDescriptor(collectRequest)
        }
        val dataModel = when (val captured = values.original(1u)) {
            is IsRootDataModel -> captured
            is IsDataModelReference<*> -> captured.get() as? IsRootDataModel
            else -> null
        } ?: throw IllegalArgumentException("Remote batch request ${type.name} has no root data model")

        @Suppress("UNCHECKED_CAST")
        val responseModel = when (type) {
            RequestType.Add -> AddResponse
            RequestType.Change -> ChangeResponse
            RequestType.Delete -> DeleteResponse
            RequestType.Get, RequestType.Scan -> maryk.core.query.responses.ValuesResponse
            RequestType.GetChanges, RequestType.ScanChanges -> ChangesResponse
            RequestType.GetUpdates, RequestType.ScanUpdates, RequestType.ScanUpdateHistory -> UpdatesResponse
            RequestType.Collect -> error("CollectRequest handled above")
        } as IsTypedObjectDataModel<in IsResponse, *, RequestContext, RequestContext>

        return BatchRequestDescriptor(responseModel, dataModel)
    }

    override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
        request: RQ,
    ): Flow<IsUpdateResponse<DM>> {
        registerLocalDataModels(listOf(request.dataModel))
        val transportable = request as? IsTransportableRequest<*>
            ?: throw IllegalArgumentException("Request ${request::class.simpleName} is not transportable")
        val context = requestContext(request.dataModel)
        val payload = RemoteStoreCodec.encode(Requests.Serializer, Requests(transportable), context, MAX_REQUEST_BODY_BYTES)

        return callbackFlow {
            val job = launch(Dispatchers.Default) {
                val useFlowProtocolV2 = flowRetryPolicy.maxReconnectAttempts > 0u ||
                    flowRetryPolicy.heartbeatTimeoutMillis != null
                var reconnectAttempts = 0u
                var reconnectDelayMillis = flowRetryPolicy.initialDelayMillis
                while (true) {
                    try {
                        var receivedFrame = false
                        var deliveredUpdate = false
                        val statement = httpClient.preparePost(buildUrl(baseUrl, RemoteStoreProtocol.flowPath)) {
                            headers {
                                append(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                                append(HttpHeaders.Accept, RemoteStoreProtocol.streamContentType)
                                appendBearerToken(bearerToken)
                                if (useFlowProtocolV2) {
                                    append(
                                        RemoteStoreProtocol.flowProtocolHeader,
                                        RemoteStoreProtocol.resumableFlowProtocol,
                                    )
                                }
                            }
                            setBody(payload)
                        }
                        suspend fun processResponse(response: HttpResponse) {
                            requireSuccess(response, "flow")
                            requireContentType(response, RemoteStoreProtocol.streamContentType, "flow")
                            val channel = response.bodyAsChannel()
                            val handle = RemoteFlowHandle(response, channel)
                            listeners.add(handle)

                            try {
                                val lengthBuffer = ByteArray(4)
                                while (!channel.isClosedForRead) {
                                    val lengthResult = try {
                                        val heartbeatTimeoutMillis = flowRetryPolicy.heartbeatTimeoutMillis
                                        if (heartbeatTimeoutMillis == null) {
                                            readFrameLength(channel, lengthBuffer)
                                        } else {
                                            withTimeout(heartbeatTimeoutMillis) {
                                                readFrameLength(channel, lengthBuffer)
                                            }
                                        }
                                    } catch (error: TimeoutCancellationException) {
                                        if (!currentCoroutineContext().isActive) {
                                            throw error
                                        }
                                        throw RemoteFlowDisconnectedException(
                                            "Remote store flow heartbeat timed out"
                                        )
                                    } ?: break
                                    if (lengthResult.length < 0) {
                                        throw IllegalStateException("Invalid streamed response length prefix: ${lengthResult.length}")
                                    }
                                    if (lengthResult.length == 0) {
                                        if (useFlowProtocolV2) {
                                            continue
                                        }
                                        throw IllegalStateException("Invalid streamed response length prefix: 0")
                                    }
                                    if (lengthResult.length > MAX_FRAME_SIZE_BYTES) {
                                        throw IllegalStateException("Streamed response frame exceeds max size: ${lengthResult.length} > $MAX_FRAME_SIZE_BYTES")
                                    }
                                    val messageBytes = ByteArray(lengthResult.length)
                                    try {
                                        val heartbeatTimeoutMillis = flowRetryPolicy.heartbeatTimeoutMillis
                                        if (heartbeatTimeoutMillis == null) {
                                            readFramePayload(channel, messageBytes)
                                        } else {
                                            withTimeout(heartbeatTimeoutMillis) {
                                                readFramePayload(channel, messageBytes)
                                            }
                                        }
                                    } catch (error: TimeoutCancellationException) {
                                        if (!currentCoroutineContext().isActive) {
                                            throw error
                                        }
                                        throw RemoteFlowDisconnectedException(
                                            "Remote store flow frame payload timed out"
                                        )
                                    }
                                    val responseContext = requestContext(request.dataModel)
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
                                            sendResult.exceptionOrNull()?.let { throw it }
                                            throw RemoteFlowBackpressureException(
                                                "Remote store flow terminated because collector backpressure prevented update delivery"
                                            )
                                        }
                                        yield()
                                        deliveredUpdate = true
                                    }
                                    receivedFrame = true
                                }
                            } finally {
                                handle.close()
                                listeners.remove(handle)
                            }
                        }
                        val heartbeatTimeoutMillis = flowRetryPolicy.heartbeatTimeoutMillis
                        if (heartbeatTimeoutMillis == null) {
                            statement.execute(::processResponse)
                        } else {
                            supervisorScope {
                                val headersReceived = CompletableDeferred<Unit>()
                                val flowExecution = async {
                                    statement.execute { response ->
                                        headersReceived.complete(Unit)
                                        processResponse(response)
                                    }
                                }
                                flowExecution.invokeOnCompletion { cause ->
                                    if (cause != null) {
                                        headersReceived.completeExceptionally(cause)
                                    }
                                }
                                try {
                                    try {
                                        withTimeout(heartbeatTimeoutMillis) {
                                            headersReceived.await()
                                        }
                                    } catch (_: TimeoutCancellationException) {
                                        flowExecution.cancel()
                                        throw RemoteFlowDisconnectedException(
                                            "Remote store flow timed out waiting for response headers"
                                        )
                                    }
                                    flowExecution.await()
                                } finally {
                                    flowExecution.cancel()
                                }
                            }
                        }
                        if (deliveredUpdate) {
                            reconnectAttempts = 0u
                            reconnectDelayMillis = flowRetryPolicy.initialDelayMillis
                        }
                        if (flowRetryPolicy.maxReconnectAttempts == 0u) {
                            return@launch
                        }
                        throw RemoteFlowDisconnectedException(
                            if (receivedFrame) {
                                "Remote store flow disconnected"
                            } else {
                                "Remote store flow ended before its first update"
                            }
                        )
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        error.rethrowIfFatal()
                        if (
                            !isRetryableRemoteFlowFailure(error) ||
                            reconnectAttempts >= flowRetryPolicy.maxReconnectAttempts
                        ) {
                            throw error
                        }
                        reconnectAttempts++
                        if (reconnectDelayMillis > 0) {
                            delay(reconnectDelayMillis)
                        }
                        reconnectDelayMillis = (
                            reconnectDelayMillis.toDouble() * flowRetryPolicy.backoffMultiplier
                        ).toLong().coerceAtMost(flowRetryPolicy.maxDelayMillis)
                    }
                }
            }
            listeners.track(job) { cause ->
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
        registerLocalDataModels(listOf(updateResponse.dataModel))
        val context = requestContext(updateResponse.dataModel)
        val payload = RemoteStoreCodec.encode(UpdateResponse.Serializer, updateResponse, context, MAX_REQUEST_BODY_BYTES)
        val responseBytes = readNonFlowResponseBytes("process-update") {
            httpClient.post(buildUrl(baseUrl, RemoteStoreProtocol.processUpdatePath)) {
                headers {
                    append(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                    append(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                    appendBearerToken(bearerToken)
                }
                setBody(payload)
            }
        }
        if (responseBytes.isEmpty()) {
            throw IllegalStateException("Remote store process-update returned an empty payload")
        }

        val responseContext = requestContext(updateResponse.dataModel)
        val remoteResponse = RemoteStoreCodec.decode(RemoteProcessResponse.Serializer, responseBytes, responseContext)
        @Suppress("UNCHECKED_CAST")
        return ProcessResponse(remoteResponse.version, remoteResponse.result as IsDataModelResponse<DM>)
    }

    override suspend fun captureSnapshotVersion(): ULong {
        val responseBytes = readNonFlowResponseBytes("snapshot version") {
            httpClient.get(buildUrl(baseUrl, RemoteStoreProtocol.snapshotVersionPath)) {
                headers {
                    append(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                    appendBearerToken(bearerToken)
                }
            }
        }
        return try {
            RemoteStoreCodec.decodeVersion(responseBytes)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("Remote store returned an invalid snapshot version", error)
        }
    }

    override suspend fun getMigrationStatuses(): Map<UInt, MigrationRuntimeStatus> =
        executeMigrationAdmin(RemoteMigrationRequest(RemoteMigrationOperation.Status)).statuses

    override suspend fun getMigrationMetrics(): Map<UInt, MigrationMetrics> =
        executeMigrationAdmin(RemoteMigrationRequest(RemoteMigrationOperation.Status)).metrics

    override suspend fun getMigrationSnapshot(): MigrationAdminSnapshot {
        val response = executeMigrationAdmin(RemoteMigrationRequest(RemoteMigrationOperation.Status))
        return MigrationAdminSnapshot(response.statuses, response.metrics)
    }

    override suspend fun requestMigrationPause(modelId: UInt): Boolean =
        executeMigrationAdmin(RemoteMigrationRequest(RemoteMigrationOperation.Pause, modelId)).accepted == true

    override suspend fun requestMigrationResume(modelId: UInt): Boolean =
        executeMigrationAdmin(RemoteMigrationRequest(RemoteMigrationOperation.Resume, modelId)).accepted == true

    override suspend fun requestMigrationCancel(modelId: UInt, reason: String): Boolean =
        executeMigrationAdmin(RemoteMigrationRequest(RemoteMigrationOperation.Cancel, modelId, reason)).accepted == true

    private suspend fun executeMigrationAdmin(request: RemoteMigrationRequest): RemoteMigrationResponse {
        val responseBytes = readNonFlowResponseBytes("migration administration") {
            httpClient.post(buildUrl(baseUrl, RemoteStoreProtocol.migrationsPath)) {
                headers {
                    append(HttpHeaders.ContentType, RemoteStoreProtocol.contentType)
                    append(HttpHeaders.Accept, RemoteStoreProtocol.contentType)
                    appendBearerToken(bearerToken)
                }
                setBody(RemoteMigrationAdminCodec.encodeRequest(request))
            }
        }
        return RemoteMigrationAdminCodec.decodeResponse(
            responseBytes,
            request.operation,
        )
    }

    override suspend fun close() {
        withContext(NonCancellable) {
            try {
                listeners.close()
            } finally {
                try {
                    sshTunnel?.close()
                } finally {
                    if (ownsClient) {
                        httpClient.close()
                    }
                }
            }
        }
    }

    override suspend fun closeAllListeners() {
        listeners.closeAll()
    }

    private suspend fun registerLocalDataModels(models: Collection<IsRootDataModel>) {
        definitionsMutex.withLock {
            models.forEach { model ->
                if (isDecodedRemoteModel(model)) {
                    return@forEach
                }
                val existing = localDataModelsByName[model.Meta.name]
                if (existing != null && existing !== model) {
                    throw IllegalArgumentException(
                        "Remote store model `${model.Meta.name}` was already bound to a different local definition"
                    )
                }
                localDataModelsByName[model.Meta.name] = model
            }
        }
    }

    private fun requestContext(
        dataModel: IsRootDataModel,
        localModels: Collection<IsRootDataModel> = listOf(dataModel),
    ): RequestContext {
        val requestDefinitions = DefinitionsConversionContext(
            DefinitionsContext(
                dataModels = definitionsContext.dataModels.toMutableMap(),
                enums = definitionsContext.enums.toMutableMap(),
                currentDefinitionName = definitionsContext.currentDefinitionName,
                typeEnums = definitionsContext.typeEnums.toMutableMap(),
            )
        )
        localModels.filterNot(::isDecodedRemoteModel).forEach { model ->
            requestDefinitions.dataModels[model.Meta.name] = DataModelReference(model)
        }
        return RequestContext(requestDefinitions, dataModel = dataModel)
    }

    private fun isDecodedRemoteModel(model: IsRootDataModel): Boolean =
        dataModelsById.values.any { it === model }
}

internal fun isRetryableRemoteFlowFailure(error: Throwable): Boolean =
    error is RemoteFlowDisconnectedException || error is IOException

private fun HeadersBuilder.appendBearerToken(bearerToken: String?) {
    if (bearerToken != null) {
        append(HttpHeaders.Authorization, "Bearer $bearerToken")
    }
}

internal class RemoteListenerRegistry {
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val listeners = mutableSetOf<RemoteFlowHandle>()
    private val jobs = mutableSetOf<Job>()
    private var closed = false

    suspend fun add(handle: RemoteFlowHandle) = mutex.withLock {
        if (closed) handle.close() else listeners.add(handle)
    }

    suspend fun remove(handle: RemoteFlowHandle) = mutex.withLock { listeners.remove(handle) }

    suspend fun add(job: Job) = mutex.withLock {
        if (closed) job.cancel() else jobs.add(job)
    }

    suspend fun remove(job: Job) = mutex.withLock { jobs.remove(job) }

    suspend fun track(job: Job, onCompletion: (Throwable?) -> Unit) {
        add(job)
        job.invokeOnCompletion { cause ->
            cleanupScope.launch {
                remove(job)
            }
            onCompletion(cause)
        }
    }

    internal suspend fun trackedJobCount(): Int = mutex.withLock { jobs.size }

    suspend fun closeAll() {
        val (handles, flowJobs) = mutex.withLock {
            val handles = listeners.toList()
            val flowJobs = jobs.toList()
            listeners.clear()
            jobs.clear()
            handles to flowJobs
        }
        flowJobs.forEach { it.cancel() }
        handles.forEach { it.close() }
    }

    suspend fun close() {
        val (handles, flowJobs) = mutex.withLock {
            closed = true
            val handles = listeners.toList()
            val flowJobs = jobs.toList()
            listeners.clear()
            jobs.clear()
            handles to flowJobs
        }
        handles.forEach { it.close() }
        flowJobs.forEach { it.cancel() }
        cleanupScope.cancel()
    }
}

internal data class RemoteFlowHandle(
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
        val bodyPreview = runCatchingNonFatal { readErrorPreview(response) }
            .getOrNull()
            ?.replace(Regex("\\s+"), " ")
            ?.take(200)
        val suffix = if (bodyPreview.isNullOrBlank()) "" else ": $bodyPreview"
        throw IllegalStateException(
            "Remote store $operation failed with HTTP ${response.status.value} ${response.status.description}$suffix"
        )
    }
}

private suspend fun readErrorPreview(response: HttpResponse): String {
    val bytes = response.bodyAsChannel()
        .readRemaining(MAX_ERROR_PREVIEW_BYTES.toLong() + 1L)
        .readByteArray()
    response.call.cancel()
    return bytes.copyOf(minOf(bytes.size, MAX_ERROR_PREVIEW_BYTES)).decodeToString()
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

private suspend fun readResponseBytes(
    response: HttpResponse,
    operation: String,
    maxBytes: Int = MAX_FRAME_SIZE_BYTES,
): ByteArray {
    val rawContentLength = response.headers[HttpHeaders.ContentLength]
    val contentLength = rawContentLength?.toLongOrNull()
    if (rawContentLength != null) {
        if (contentLength == null || contentLength < 0L) {
            response.call.cancel()
            throw IllegalStateException("Remote store $operation response has invalid Content-Length header")
        }
        if (contentLength > maxBytes.toLong()) {
            response.call.cancel()
            throw IllegalStateException("Remote store $operation response exceeds max size: $contentLength > $maxBytes")
        }
    }
    val bytes = response.bodyAsChannel()
        .readRemaining(maxBytes.toLong() + 1L)
        .readByteArray()
    if (bytes.size > maxBytes) {
        response.call.cancel()
        throw IllegalStateException("Remote store $operation response exceeds max size: ${bytes.size} > $maxBytes")
    }
    return bytes
}

private suspend fun readNonFlowResponseBytes(
    operation: String,
    maxBytes: Int = MAX_FRAME_SIZE_BYTES,
    request: suspend () -> HttpResponse,
): ByteArray = executeNonFlowRequest(request = request) { response ->
    requireSuccess(response, operation)
    requireContentType(response, RemoteStoreProtocol.contentType, operation)
    readResponseBytes(response, operation, maxBytes)
}

internal suspend fun <Response, Result> executeNonFlowRequest(
    timeoutMillis: Long = NON_FLOW_REQUEST_TIMEOUT_MILLIS,
    request: suspend () -> Response,
    consumeResponse: suspend (Response) -> Result,
): Result = withTimeout(timeoutMillis) {
    consumeResponse(request())
}

private const val MAX_FRAME_SIZE_BYTES = 16 * 1024 * 1024
private const val MAX_REQUEST_BODY_BYTES = 16 * 1024 * 1024
private const val NON_FLOW_REQUEST_TIMEOUT_MILLIS = 30_000L
private const val MAX_BATCH_RESPONSE_BODY_BYTES = 64 * 1024 * 1024
private const val MAX_ERROR_PREVIEW_BYTES = 4096

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
            it.rethrowIfFatal()
            throw RemoteFlowDisconnectedException("Stream ended while reading frame length prefix", it)
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
        it.rethrowIfFatal()
        throw RemoteFlowDisconnectedException("Stream ended while reading frame payload", it)
    }
}
