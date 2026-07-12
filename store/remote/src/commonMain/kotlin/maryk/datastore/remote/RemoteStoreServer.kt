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
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import maryk.core.models.IsRootDataModel
import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.RequestContext
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.IsTransportableRequest
import maryk.core.query.requests.Requests
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.values.ObjectValues
import maryk.datastore.shared.IsDataStore
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
    if (!host.isLoopbackHost() && config.bearerToken == null && !config.allowInsecureRemoteBinding) {
        throw IllegalArgumentException(
            "Remote Store refuses an unauthenticated non-loopback bind; configure a bearer token " +
                "or explicitly allow insecure remote binding"
        )
    }
}

private fun String.isLoopbackHost(): Boolean = when (lowercase()) {
    "localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1" -> true
    else -> false
}

internal fun Application.remoteStoreModule(
    dataStore: IsDataStore,
    config: RemoteStoreServerConfig = RemoteStoreServerConfig(),
) {
    if (config.bearerToken != null && config.bearerToken.isBlank()) {
        throw IllegalArgumentException("Remote Store bearer token cannot be blank")
    }
    val info = buildRemoteStoreInfo(dataStore)

    routing {
        get(RemoteStoreProtocol.infoPath) {
            if (!call.requireAuthorization(config.bearerToken)) return@get
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

        post(RemoteStoreProtocol.executePath) {
            if (!call.requireAuthorization(config.bearerToken)) return@post
            call.respondValidationErrors {
                requireRequestContentType(call, RemoteStoreProtocol.contentType)
                val requestBytes = readRequestBytes(call, "execute")
                val requestContext = createRequestContext(dataStore)
                val requests = decodeRequest(
                    operation = "execute",
                    decode = { RemoteStoreCodec.decode(Requests.Serializer, requestBytes, requestContext) },
                )

                @Suppress("UNCHECKED_CAST")
                val rawRequests = requests.requests as List<Any>
                if (rawRequests.isEmpty()) {
                    throw RequestValidationException(HttpStatusCode.BadRequest, "Remote execute request list cannot be empty")
                }
                val responseChunks = ArrayList<ByteArray>(rawRequests.size * 2)
                var totalSize = 0
                for (rawRequest in rawRequests) {
                    val request = resolveRequest(rawRequest, operation = "execute")
                    @Suppress("UNCHECKED_CAST")
                    val storeRequest = request as? IsStoreRequest<IsRootDataModel, IsResponse>
                        ?: throw RequestValidationException(
                            HttpStatusCode.BadRequest,
                            "Remote execute only accepts store requests"
                        )
                    val response = dataStore.execute(storeRequest)
                    val dataModel = storeRequest.dataModel
                    val responseContext = RequestContext(requestContext.definitionsContext, dataModel = dataModel)
                    @Suppress("UNCHECKED_CAST")
                    val responseBytes = RemoteStoreCodec.encode(
                        request.responseModel.Serializer as IsObjectDataModelSerializer<Any, *, RequestContext, RequestContext>,
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
                    if (totalSize > MAX_RESPONSE_BODY_BYTES - chunkSize) {
                        throw RequestValidationException(
                            HttpStatusCode.PayloadTooLarge,
                            "Remote execute response exceeds max size: ${totalSize + chunkSize} > $MAX_RESPONSE_BODY_BYTES"
                        )
                    }
                    responseChunks.add(lengthPrefix)
                    responseChunks.add(responseBytes)
                    totalSize += chunkSize
                }
                val responseBytes = ByteArray(totalSize)
                var offset = 0
                for (chunk in responseChunks) {
                    chunk.copyInto(responseBytes, offset)
                    offset += chunk.size
                }
                call.respondBytes(responseBytes, ContentType.parse(RemoteStoreProtocol.contentType))
            }
        }

        post(RemoteStoreProtocol.flowPath) {
            if (!call.requireAuthorization(config.bearerToken)) return@post
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

                @Suppress("UNCHECKED_CAST")
                val typedFetch = fetchRequest as IsFlowRequest<IsRootDataModel, IsDataResponse<IsRootDataModel>>
                val updates = dataStore.executeFlow(typedFetch)

                call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                    updates.collect { update ->
                        val response = UpdatesResponse(fetchRequest.dataModel, listOf(update))
                        val responseContext = RequestContext(requestContext.definitionsContext, dataModel = fetchRequest.dataModel)
                        val responseBytes = RemoteStoreCodec.encode(UpdatesResponse.Serializer, response, responseContext, MAX_FRAME_SIZE_BYTES)
                        writeFully(RemoteStoreCodec.lengthPrefix(responseBytes.size))
                        writeFully(responseBytes)
                        flush()
                    }
                }
            }
        }

        post(RemoteStoreProtocol.processUpdatePath) {
            if (!call.requireAuthorization(config.bearerToken)) return@post
            call.respondValidationErrors {
                requireRequestContentType(call, RemoteStoreProtocol.contentType)
                val requestBytes = readRequestBytes(call, "process-update")
                val requestContext = createRequestContext(dataStore)
                val updateRequest = decodeRequest(
                    operation = "process-update",
                    decode = { RemoteStoreCodec.decode(UpdateResponse.Serializer, requestBytes, requestContext) },
                )
                val response = dataStore.processUpdate(updateRequest)
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
    }
}

private suspend fun ApplicationCall.requireAuthorization(bearerToken: String?): Boolean {
    if (bearerToken == null) return true

    val supplied = request.headers[HttpHeaders.Authorization]
    val authorized = supplied != null && constantTimeEquals(supplied, "Bearer $bearerToken")
    if (!authorized) {
        respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
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
private const val MAX_RESPONSE_BODY_BYTES = MAX_FRAME_SIZE_BYTES + 4

private suspend inline fun ApplicationCall.respondValidationErrors(
    crossinline block: suspend () -> Unit,
) {
    try {
        block()
    } catch (error: RequestValidationException) {
        respondText(error.message, status = error.status)
    }
}
