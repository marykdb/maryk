package maryk.datastore.remote

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.io.readByteArray
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.asValues
import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.RequestContext
import maryk.core.query.requests.CollectRequest
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.IsTransportableRequest
import maryk.core.query.requests.Requests
import maryk.core.query.requests.RequestType
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsDataModelResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.properties.types.TypedValue
import maryk.core.values.ObjectValues
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.captureSnapshotVersion
import maryk.datastore.shared.migration.MigrationAdmin
import maryk.datastore.shared.rethrowIfFatal

class RemoteStoreServer(
    private val dataStore: IsDataStore,
) {
    fun start(
        host: String,
        port: Int,
        wait: Boolean = true,
        config: RemoteStoreServerConfig = RemoteStoreServerConfig(),
    ): RemoteStoreServerHandle {
        validateRemoteStoreServerBinding(host, config)
        val engine = embeddedServer(CIO, host = host, port = port) {
            remoteStoreModule(dataStore, config)
        }
        engine.start(wait = wait)
        return KtorRemoteStoreServerHandle(engine)
    }
}

interface RemoteStoreServerHandle {
    fun stop(gracePeriodMillis: Long = 0, timeoutMillis: Long = 0)
}

private class KtorRemoteStoreServerHandle(
    private val engine: EmbeddedServer<*, *>,
) : RemoteStoreServerHandle {
    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        engine.stop(gracePeriodMillis, timeoutMillis)
    }
}

fun validateRemoteStoreServerBinding(host: String, config: RemoteStoreServerConfig) {
    if (host.isBlank()) {
        throw IllegalArgumentException("Remote Store host cannot be blank")
    }
    if (config.bearerToken != null && config.bearerToken.isBlank()) {
        throw IllegalArgumentException("Remote Store bearer token cannot be blank")
    }
    require(config.flowHeartbeatMillis == null || config.flowHeartbeatMillis > 0) {
        "Remote Store flow heartbeat interval must be positive"
    }
    if (!host.isLoopbackRemoteHost() && !config.allowInsecureRemoteBinding) {
        throw IllegalArgumentException(
            "Remote Store refuses a non-loopback plaintext bind; use a loopback bind behind TLS or SSH, " +
                "or explicitly allow insecure remote binding"
        )
    }
}

internal fun Application.remoteStoreModule(
    dataStore: IsDataStore,
    config: RemoteStoreServerConfig = RemoteStoreServerConfig(),
) {
    if (config.bearerToken != null && config.bearerToken.isBlank()) {
        throw IllegalArgumentException("Remote Store bearer token cannot be blank")
    }
    require(config.flowHeartbeatMillis == null || config.flowHeartbeatMillis > 0) {
        "Remote Store flow heartbeat interval must be positive"
    }
    val info = buildRemoteStoreInfo(dataStore)

    routing {
        get(RemoteStoreProtocol.infoPath) {
            val principal = call.authenticate(config) ?: return@get
            if (!call.authorize(config, principal, RemoteStoreOperation.Info)) return@get
            call.respondValidationErrors {
                val bytes = RemoteStoreCodec.encode(
                    RemoteStoreInfo.Serializer,
                    info,
                    DefinitionsConversionContext(),
                    MAX_FRAME_SIZE_BYTES,
                )
                call.respondBytes(bytes, ContentType.parse(RemoteStoreProtocol.contentType))
            }
        }

        get(RemoteStoreProtocol.snapshotVersionPath) {
            val principal = call.authenticate(config) ?: return@get
            if (
                !call.authorize(
                    config,
                    principal,
                    RemoteStoreOperation.SnapshotVersion,
                )
            ) return@get
            call.respondValidationErrors {
                call.respondBytes(
                    RemoteStoreCodec.encodeVersion(dataStore.captureSnapshotVersion()),
                    ContentType.parse(RemoteStoreProtocol.contentType),
                )
            }
        }

        post(RemoteStoreProtocol.executePath) {
            val principal = call.authenticate(config) ?: return@post
            call.respondValidationErrors {
                requireRequestContentType(call, RemoteStoreProtocol.contentType)
                val requestBytes = readRequestBytes(call, "execute")
                val requestContext = createRequestContext(dataStore)
                val requests = decodeRequest(
                    operation = "execute",
                    decode = { RemoteStoreCodec.decodeValues(Requests.Serializer, requestBytes, requestContext) },
                )

                @Suppress("UNCHECKED_CAST")
                val rawRequests = (
                    requests.original(Requests.requests.index) as? List<TypedValue<*, Any>>
                )?.map { it.value }.orEmpty()
                if (rawRequests.isEmpty()) {
                    throw RequestValidationException(HttpStatusCode.BadRequest, "Remote execute request list cannot be empty")
                }
                val useBatchProtocol =
                    call.request.headers[RemoteStoreProtocol.executeProtocolHeader] ==
                        RemoteStoreProtocol.batchExecuteProtocol ||
                        rawRequests.size > 1
                val responseChunks = ArrayList<ByteArray>(rawRequests.size * 2)
                var totalSize = 0
                for (rawRequest in rawRequests) {
                    val request = resolveRequest(rawRequest, operation = "execute")
                    val executableRequest = if (request is CollectRequest<*, *>) {
                        request.request
                    } else {
                        request
                    }
                    @Suppress("UNCHECKED_CAST")
                    val storeRequest = executableRequest as? IsStoreRequest<IsRootDataModel, IsResponse>
                        ?: throw RequestValidationException(
                            HttpStatusCode.BadRequest,
                            "Remote execute only accepts store requests"
                        )
                    val authorized = config.authorizer?.authorize(
                        RemoteStoreAuthorizationRequest(
                            principal = principal,
                            operation = RemoteStoreOperation.Execute,
                            requestType = executableRequest.requestType,
                            modelName = storeRequest.dataModel.Meta.name,
                        )
                    ) ?: true
                    val response = if (authorized) {
                        dataStore.execute(storeRequest)
                    } else {
                        authorizationFailure(storeRequest)
                            ?: throw RequestValidationException(
                                HttpStatusCode.Forbidden,
                                "Remote execute operation is not authorized"
                            )
                    }
                    if (request is CollectRequest<*, *>) {
                        requestContext.addToCollect(request.name, request.request)
                        @Suppress("UNCHECKED_CAST")
                        val responseModel = request.responseModel as IsObjectDataModel<IsResponse>
                        requestContext.collectResult(
                            request.name,
                            responseModel.asValues(response, requestContext),
                        )
                    }
                    val dataModel = storeRequest.dataModel
                    val responseContext = RequestContext(requestContext.definitionsContext, dataModel = dataModel)
                    @Suppress("UNCHECKED_CAST")
                    val responseBytes = RemoteStoreCodec.encode(
                        executableRequest.responseModel.Serializer as IsObjectDataModelSerializer<Any, *, RequestContext, RequestContext>,
                        response as Any,
                        responseContext,
                        MAX_FRAME_SIZE_BYTES,
                    )
                    if (responseBytes.isEmpty()) {
                        throw IllegalStateException("Remote execute response cannot be empty")
                    }
                    if (responseBytes.size > MAX_FRAME_SIZE_BYTES) {
                        throw IllegalStateException(
                            "Remote execute response frame exceeds max size: ${responseBytes.size} > $MAX_FRAME_SIZE_BYTES"
                        )
                    }
                    val lengthPrefix = RemoteStoreCodec.lengthPrefix(responseBytes.size)
                    val chunkSize = lengthPrefix.size + responseBytes.size
                    if (totalSize > MAX_BATCH_RESPONSE_BODY_BYTES - chunkSize) {
                        throw RequestValidationException(
                            HttpStatusCode.PayloadTooLarge,
                            "Remote execute response exceeds max size: ${totalSize + chunkSize} > $MAX_BATCH_RESPONSE_BODY_BYTES"
                        )
                    }
                    responseChunks.add(lengthPrefix)
                    responseChunks.add(responseBytes)
                    totalSize += chunkSize
                }
                val responseBytes = if (!useBatchProtocol) {
                    responseChunks[1]
                } else {
                    ByteArray(totalSize).also { bytes ->
                        var offset = 0
                        for (chunk in responseChunks) {
                            chunk.copyInto(bytes, offset)
                            offset += chunk.size
                        }
                    }
                }
                call.respondBytes(responseBytes, ContentType.parse(RemoteStoreProtocol.contentType))
            }
        }

        post(RemoteStoreProtocol.flowPath) {
            val principal = call.authenticate(config) ?: return@post
            call.respondValidationErrors {
                requireRequestContentType(call, RemoteStoreProtocol.contentType)
                val requestBytes = readRequestBytes(call, "flow")
                val requestContext = createRequestContext(dataStore)
                val requests = decodeRequest(
                    operation = "flow",
                    decode = { RemoteStoreCodec.decode(Requests.Serializer, requestBytes, requestContext) },
                )
                @Suppress("UNCHECKED_CAST")
                val rawRequests = requests.requests as List<Any>
                val fetchRequest = rawRequests.singleOrNull()?.let { resolveRequest(it, operation = "flow") } as? IsFlowRequest<*, *>
                    ?: throw RequestValidationException(HttpStatusCode.BadRequest, "Remote flow expects a single flow request")
                if (
                    !call.authorize(
                        config = config,
                        principal = principal,
                        operation = RemoteStoreOperation.Flow,
                        requestType = (fetchRequest as? IsTransportableRequest<*>)?.requestType,
                        modelName = fetchRequest.dataModel.Meta.name,
                    )
                ) return@respondValidationErrors

                @Suppress("UNCHECKED_CAST")
                val typedFetch = fetchRequest as IsFlowRequest<IsRootDataModel, IsDataResponse<IsRootDataModel>>
                val updates = dataStore.executeFlow(typedFetch)
                val heartbeatMillis = config.flowHeartbeatMillis?.takeIf {
                    call.request.headers[RemoteStoreProtocol.flowProtocolHeader] ==
                        RemoteStoreProtocol.resumableFlowProtocol
                }

                call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                    coroutineScope {
                        val updateChannel = updates.produceIn(this)
                        try {
                            while (true) {
                                val updateResult = if (heartbeatMillis == null) {
                                    updateChannel.receiveCatching()
                                } else {
                                    withTimeoutOrNull(heartbeatMillis) {
                                        updateChannel.receiveCatching()
                                    }
                                }
                                if (updateResult == null) {
                                    writeFully(RemoteStoreCodec.lengthPrefix(0))
                                    flush()
                                    continue
                                }
                                val update = updateResult.getOrNull() ?: break
                                val response = UpdatesResponse(fetchRequest.dataModel, listOf(update))
                                val responseContext = RequestContext(requestContext.definitionsContext, dataModel = fetchRequest.dataModel)
                                val responseBytes = RemoteStoreCodec.encode(UpdatesResponse.Serializer, response, responseContext, MAX_FRAME_SIZE_BYTES)
                                writeFully(RemoteStoreCodec.lengthPrefix(responseBytes.size))
                                writeFully(responseBytes)
                                flush()
                            }
                        } finally {
                            updateChannel.cancel()
                        }
                    }
                }
            }
        }

        post(RemoteStoreProtocol.processUpdatePath) {
            val principal = call.authenticate(config) ?: return@post
            call.respondValidationErrors {
                requireRequestContentType(call, RemoteStoreProtocol.contentType)
                val requestBytes = readRequestBytes(call, "process-update")
                val requestContext = createRequestContext(dataStore)
                val decodedUpdateRequest = decodeRequest(
                    operation = "process-update",
                    decode = { RemoteStoreCodec.decode(UpdateResponse.Serializer, requestBytes, requestContext) },
                )
                @Suppress("UNCHECKED_CAST")
                val updateRequest = decodedUpdateRequest as UpdateResponse<IsRootDataModel>
                val requestType = when (updateRequest.update) {
                    is AdditionUpdate<*> -> RequestType.Add
                    is ChangeUpdate<*> -> RequestType.Change
                    is RemovalUpdate<*> -> RequestType.Delete
                    else -> null
                }
                val authorized = call.authorize(
                    config = config,
                    principal = principal,
                    operation = RemoteStoreOperation.ProcessUpdate,
                    requestType = requestType,
                    modelName = updateRequest.dataModel.Meta.name,
                    respondOnFailure = false,
                )
                val response = if (authorized) {
                    dataStore.processUpdate(updateRequest)
                } else {
                    val result = authorizationFailureForUpdate(updateRequest)
                        ?: throw RequestValidationException(
                            HttpStatusCode.Forbidden,
                            "Remote process-update operation is not authorized"
                        )
                    ProcessResponse(
                        version = updateRequest.update.version,
                        result = result,
                    )
                }
                val responseContext = RequestContext(requestContext.definitionsContext, dataModel = updateRequest.dataModel)
                val remoteResponse = RemoteProcessResponse(response.version, response.result)
                val responseBytes = RemoteStoreCodec.encode(RemoteProcessResponse.Serializer, remoteResponse, responseContext, MAX_FRAME_SIZE_BYTES)
                if (responseBytes.isEmpty()) {
                    throw IllegalStateException("Remote process-update response cannot be empty")
                }
                if (responseBytes.size > MAX_FRAME_SIZE_BYTES) {
                    throw IllegalStateException(
                        "Remote process-update response exceeds max size: ${responseBytes.size} > $MAX_FRAME_SIZE_BYTES"
                    )
                }
                call.respondBytes(responseBytes, ContentType.parse(RemoteStoreProtocol.contentType))
            }
        }

        post(RemoteStoreProtocol.migrationsPath) {
            val principal = call.authenticate(config) ?: return@post
            call.respondValidationErrors {
                requireRequestContentType(call, RemoteStoreProtocol.contentType)
                val admin = dataStore as? MigrationAdmin
                    ?: throw RequestValidationException(
                        HttpStatusCode.NotImplemented,
                        "This store does not expose migration administration"
                    )
                val request = try {
                    RemoteMigrationAdminCodec.decodeRequest(readRequestBytes(call, "migration administration"))
                } catch (error: IllegalArgumentException) {
                    throw RequestValidationException(
                        HttpStatusCode.BadRequest,
                        error.message ?: "Invalid migration administration request",
                    )
                }
                val modelId = request.modelId
                val modelName = modelId?.let { dataStore.dataModelsById[it]?.Meta?.name }
                if (
                    modelId != null &&
                    modelName == null
                ) {
                    throw RequestValidationException(HttpStatusCode.BadRequest, "Unknown model id `$modelId`")
                }
                if (
                    !call.authorize(
                        config = config,
                        principal = principal,
                        operation = request.operation.toAuthorizationOperation(),
                        modelName = modelName,
                    )
                ) return@respondValidationErrors

                val response = when (request.operation) {
                    RemoteMigrationOperation.Status -> RemoteMigrationResponse(
                        statuses = admin.getMigrationStatuses(),
                        metrics = admin.getMigrationMetrics(),
                    )
                    RemoteMigrationOperation.Pause -> RemoteMigrationResponse(
                        accepted = admin.requestMigrationPause(request.requireModelId()),
                    )
                    RemoteMigrationOperation.Resume -> RemoteMigrationResponse(
                        accepted = admin.requestMigrationResume(request.requireModelId()),
                    )
                    RemoteMigrationOperation.Cancel -> RemoteMigrationResponse(
                        accepted = admin.requestMigrationCancel(
                            request.requireModelId(),
                            request.reason?.takeIf(String::isNotBlank) ?: "Canceled by remote operator",
                        ),
                    )
                }
                call.respondBytes(
                    RemoteMigrationAdminCodec.encodeResponse(response),
                    ContentType.parse(RemoteStoreProtocol.contentType),
                )
            }
        }
    }
}

private fun RemoteMigrationOperation.toAuthorizationOperation(): RemoteStoreOperation = when (this) {
    RemoteMigrationOperation.Status -> RemoteStoreOperation.MigrationStatus
    RemoteMigrationOperation.Pause -> RemoteStoreOperation.MigrationPause
    RemoteMigrationOperation.Resume -> RemoteStoreOperation.MigrationResume
    RemoteMigrationOperation.Cancel -> RemoteStoreOperation.MigrationCancel
}

private fun RemoteMigrationRequest.requireModelId(): UInt =
    modelId ?: throw RequestValidationException(
        HttpStatusCode.BadRequest,
        "Migration control operation requires a model id",
    )

private suspend fun ApplicationCall.authenticate(
    config: RemoteStoreServerConfig,
): RemoteStorePrincipal? {
    val supplied = request.headers[HttpHeaders.Authorization]
    val principal = when {
        config.authenticator != null -> config.authenticator.authenticate(supplied)
        config.bearerToken != null && supplied != null &&
            constantTimeEquals(supplied, "Bearer ${config.bearerToken}") ->
            RemoteStorePrincipal("bearer")
        config.bearerToken == null -> RemoteStorePrincipal("anonymous")
        else -> null
    }
    if (principal == null) {
        respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
    }
    return principal
}

private suspend fun ApplicationCall.authorize(
    config: RemoteStoreServerConfig,
    principal: RemoteStorePrincipal,
    operation: RemoteStoreOperation,
    requestType: RequestType? = null,
    modelName: String? = null,
    respondOnFailure: Boolean = true,
): Boolean {
    val authorized = config.authorizer?.authorize(
        RemoteStoreAuthorizationRequest(
            principal = principal,
            operation = operation,
            requestType = requestType,
            modelName = modelName,
        )
    ) ?: true
    if (!authorized && respondOnFailure) {
        respondText("Forbidden", status = HttpStatusCode.Forbidden)
    }
    return authorized
}

private fun constantTimeEquals(left: String, right: String): Boolean {
    val leftBytes = left.encodeToByteArray()
    val rightBytes = right.encodeToByteArray()
    var difference = leftBytes.size xor rightBytes.size
    val length = maxOf(leftBytes.size, rightBytes.size)
    for (index in 0 until length) {
        val leftByte = leftBytes.getOrElse(index) { 0 }
        val rightByte = rightBytes.getOrElse(index) { 0 }
        difference = difference or (leftByte.toInt() xor rightByte.toInt())
    }
    return difference == 0
}

private fun authorizationFailure(
    request: IsStoreRequest<IsRootDataModel, IsResponse>,
): IsResponse? = when (request) {
    is AddRequest<*> -> {
        @Suppress("UNCHECKED_CAST")
        request as AddRequest<IsRootDataModel>
        AddResponse(
            request.dataModel,
            List(request.objects.size) { AuthFail<IsRootDataModel>() },
        )
    }
    is ChangeRequest<*> -> {
        @Suppress("UNCHECKED_CAST")
        request as ChangeRequest<IsRootDataModel>
        ChangeResponse(
            request.dataModel,
            List(request.objects.size) { AuthFail<IsRootDataModel>() },
        )
    }
    is DeleteRequest<*> -> {
        @Suppress("UNCHECKED_CAST")
        request as DeleteRequest<IsRootDataModel>
        DeleteResponse(
            request.dataModel,
            List(request.keys.size) { AuthFail<IsRootDataModel>() },
        )
    }
    else -> null
}

private fun authorizationFailureForUpdate(
    request: UpdateResponse<IsRootDataModel>,
): IsDataModelResponse<IsRootDataModel>? = when (request.update) {
    is AdditionUpdate<*> -> AddResponse(
        request.dataModel,
        listOf(AuthFail()),
    )
    is ChangeUpdate<*> -> ChangeResponse(
        request.dataModel,
        listOf(AuthFail()),
    )
    is RemovalUpdate<*> -> DeleteResponse(
        request.dataModel,
        listOf(AuthFail()),
    )
    else -> null
}

private fun resolveRequest(rawRequest: Any, operation: String): IsTransportableRequest<*> = when (rawRequest) {
    is IsTransportableRequest<*> -> rawRequest
    is ObjectValues<*, *> -> try {
        rawRequest.toDataObject() as IsTransportableRequest<*>
    } catch (error: Throwable) {
        error.rethrowIfFatal()
        throw RequestValidationException(
            HttpStatusCode.BadRequest,
            "Remote $operation request contains invalid transportable payload"
        )
    }
    else -> throw RequestValidationException(
        HttpStatusCode.BadRequest,
        "Remote $operation request contains unsupported payload type `${rawRequest::class.simpleName}`"
    )
}

private fun buildRemoteStoreInfo(dataStore: IsDataStore): RemoteStoreInfo {
    val definitions = RemoteDataStore.collectDefinitions(dataStore.dataModelsById.values)
    val modelIds = dataStore.dataModelsById.map { (id, model) ->
        RemoteStoreModelId(id = id, name = model.Meta.name)
    }
    return RemoteStoreInfo(
        definitions = definitions,
        modelIds = modelIds,
        keepAllVersions = dataStore.keepAllVersions,
        keepUpdateHistoryIndex = dataStore.keepUpdateHistoryIndex,
        supportsFuzzyQualifierFiltering = dataStore.supportsFuzzyQualifierFiltering,
        supportsSubReferenceFiltering = dataStore.supportsSubReferenceFiltering,
    )
}

private fun createRequestContext(dataStore: IsDataStore): RequestContext {
    val dataModels = dataStore.dataModelsById.values.associateBy { it.Meta.name }
    val context = DefinitionsContext(dataModels = dataModels.mapValues { DataModelReference(it.value) }.toMutableMap())
    return RequestContext(context)
}

private suspend fun readRequestBytes(call: ApplicationCall, operation: String): ByteArray {
    val rawContentLength = call.request.headers[HttpHeaders.ContentLength]
    val contentLength = rawContentLength?.toLongOrNull()
    if (rawContentLength != null && contentLength == null) {
        throw RequestValidationException(
            HttpStatusCode.BadRequest,
            "Remote $operation request has invalid Content-Length header"
        )
    }
    if (contentLength != null) {
        if (contentLength < 0L) {
            throw RequestValidationException(
                HttpStatusCode.BadRequest,
                "Remote $operation request has invalid Content-Length header"
            )
        }
        if (contentLength == 0L) {
            throw RequestValidationException(HttpStatusCode.BadRequest, "Remote $operation request payload cannot be empty")
        }
        if (contentLength > MAX_REQUEST_BODY_BYTES.toLong()) {
            throw RequestValidationException(
                HttpStatusCode.PayloadTooLarge,
                "Remote $operation request payload exceeds max size: $contentLength > $MAX_REQUEST_BODY_BYTES"
            )
        }
    }
    val bytes = call.receiveChannel()
        .readRemaining(MAX_REQUEST_BODY_BYTES.toLong() + 1L)
        .readByteArray()
    if (bytes.isEmpty()) {
        throw RequestValidationException(HttpStatusCode.BadRequest, "Remote $operation request payload cannot be empty")
    }
    if (bytes.size > MAX_REQUEST_BODY_BYTES) {
        throw RequestValidationException(
            HttpStatusCode.PayloadTooLarge,
            "Remote $operation request payload exceeds max size: ${bytes.size} > $MAX_REQUEST_BODY_BYTES"
        )
    }
    return bytes
}

private fun requireRequestContentType(call: ApplicationCall, expected: String) {
    val contentType = call.request.headers[HttpHeaders.ContentType]
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
    if (contentType != expected.lowercase()) {
        throw RequestValidationException(
            HttpStatusCode.UnsupportedMediaType,
            "Content-Type must be `$expected`"
        )
    }
}

private inline fun <T> decodeRequest(operation: String, decode: () -> T): T {
    return try {
        decode()
    } catch (error: Throwable) {
        error.rethrowIfFatal()
        throw RequestValidationException(HttpStatusCode.BadRequest, "Remote $operation payload is invalid")
    }
}

private class RequestValidationException(
    val status: HttpStatusCode,
    override val message: String,
) : IllegalArgumentException(message)

private const val MAX_REQUEST_BODY_BYTES = 16 * 1024 * 1024
private const val MAX_FRAME_SIZE_BYTES = 16 * 1024 * 1024
private const val MAX_BATCH_RESPONSE_BODY_BYTES = 64 * 1024 * 1024

private suspend inline fun ApplicationCall.respondValidationErrors(
    crossinline block: suspend () -> Unit,
) {
    try {
        block()
    } catch (error: RequestValidationException) {
        respondText(error.message, status = error.status)
    }
}
