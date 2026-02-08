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
import maryk.core.query.requests.IsFetchRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.IsTransportableRequest
import maryk.core.query.requests.Requests
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.values.ObjectValues
import maryk.datastore.shared.IsDataStore

class RemoteStoreServer(
    private val dataStore: IsDataStore,
) {
    fun start(host: String, port: Int, wait: Boolean = true): RemoteStoreServerHandle {
        val engine = embeddedServer(CIO, host = host, port = port) {
            remoteStoreModule(dataStore)
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

internal fun Application.remoteStoreModule(dataStore: IsDataStore) {
    val info = buildRemoteStoreInfo(dataStore)

    routing {
        get(RemoteStoreProtocol.infoPath) {
            call.respondValidationErrors {
                val bytes = RemoteStoreCodec.encode(
                    RemoteStoreInfo.Serializer,
                    info,
                    DefinitionsConversionContext(),
                )
                call.respondBytes(bytes, ContentType.parse(RemoteStoreProtocol.contentType))
            }
        }

        post(RemoteStoreProtocol.executePath) {
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
                    responseChunks.add(lengthPrefix)
                    responseChunks.add(responseBytes)
                    totalSize += lengthPrefix.size + responseBytes.size
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
                val fetchRequest = rawRequests.singleOrNull()?.let { resolveRequest(it, operation = "flow") } as? IsFetchRequest<*, *>
                    ?: throw RequestValidationException(HttpStatusCode.BadRequest, "Remote flow expects a single fetch request")

                @Suppress("UNCHECKED_CAST")
                val typedFetch = fetchRequest as IsFetchRequest<IsRootDataModel, IsDataResponse<IsRootDataModel>>
                val updates = dataStore.executeFlow(typedFetch)

                call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                    updates.collect { update ->
                        val response = UpdatesResponse(fetchRequest.dataModel, listOf(update))
                        val responseContext = RequestContext(requestContext.definitionsContext, dataModel = fetchRequest.dataModel)
                        val responseBytes = RemoteStoreCodec.encode(UpdatesResponse.Serializer, response, responseContext)
                        if (responseBytes.size > MAX_FRAME_SIZE_BYTES) {
                            throw IllegalStateException(
                                "Remote flow response frame exceeds max size: ${responseBytes.size} > $MAX_FRAME_SIZE_BYTES"
                            )
                        }
                        writeFully(RemoteStoreCodec.lengthPrefix(responseBytes.size))
                        writeFully(responseBytes)
                        flush()
                    }
                }
            }
        }

        post(RemoteStoreProtocol.processUpdatePath) {
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
                val responseBytes = RemoteStoreCodec.encode(RemoteProcessResponse.Serializer, remoteResponse, responseContext)
                if (responseBytes.isEmpty()) {
                    throw IllegalStateException("Remote process-update response cannot be empty")
                }
                call.respondBytes(responseBytes, ContentType.parse(RemoteStoreProtocol.contentType))
            }
        }
    }
}

private fun resolveRequest(rawRequest: Any, operation: String): IsTransportableRequest<*> = when (rawRequest) {
    is IsTransportableRequest<*> -> rawRequest
    is ObjectValues<*, *> -> runCatching { rawRequest.toDataObject() as IsTransportableRequest<*> }
        .getOrElse {
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
    return runCatching(decode).getOrElse {
        throw RequestValidationException(HttpStatusCode.BadRequest, "Remote $operation payload is invalid")
    }
}

private class RequestValidationException(
    val status: HttpStatusCode,
    override val message: String,
) : IllegalArgumentException(message)

private const val MAX_REQUEST_BODY_BYTES = 16 * 1024 * 1024
private const val MAX_FRAME_SIZE_BYTES = 16 * 1024 * 1024

private suspend inline fun ApplicationCall.respondValidationErrors(
    crossinline block: suspend () -> Unit,
) {
    try {
        block()
    } catch (error: RequestValidationException) {
        respondText(error.message, status = error.status)
    }
}
