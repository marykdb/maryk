package maryk.datastore.remote

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
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
            val bytes = RemoteStoreCodec.encode(
                RemoteStoreInfo.Serializer,
                info,
                DefinitionsConversionContext(),
            )
            call.respondBytes(bytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }

        post(RemoteStoreProtocol.executePath) {
            val requestBytes = call.receiveChannel().readRemaining().readByteArray()
            val requestContext = createRequestContext(dataStore)
            val requests = RemoteStoreCodec.decode(Requests.Serializer, requestBytes, requestContext)

            @Suppress("UNCHECKED_CAST")
            val rawRequests = requests.requests as List<Any>
            val responseChunks = ArrayList<ByteArray>(rawRequests.size * 2)
            var totalSize = 0
            for (rawRequest in rawRequests) {
                val request = resolveRequest(rawRequest)
                @Suppress("UNCHECKED_CAST")
                val storeRequest = request as IsStoreRequest<IsRootDataModel, IsResponse>
                val response = dataStore.execute(storeRequest)
                val dataModel = storeRequest.dataModel
                val responseContext = RequestContext(requestContext.definitionsContext, dataModel = dataModel)
                @Suppress("UNCHECKED_CAST")
                val responseBytes = RemoteStoreCodec.encode(
                    request.responseModel.Serializer as IsObjectDataModelSerializer<Any, *, RequestContext, RequestContext>,
                    response as Any,
                    responseContext,
                )
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

        post(RemoteStoreProtocol.flowPath) {
            val requestBytes = call.receiveChannel().readRemaining().readByteArray()
            val requestContext = createRequestContext(dataStore)
            val requests = RemoteStoreCodec.decode(Requests.Serializer, requestBytes, requestContext)
            @Suppress("UNCHECKED_CAST")
            val rawRequests = requests.requests as List<Any>
            val fetchRequest = rawRequests.singleOrNull()?.let { resolveRequest(it) } as? IsFetchRequest<*, *>
                ?: return@post call.respondText("Expected a single fetch request", status = HttpStatusCode.BadRequest)

            @Suppress("UNCHECKED_CAST")
            val typedFetch = fetchRequest as IsFetchRequest<IsRootDataModel, IsDataResponse<IsRootDataModel>>
            val updates = dataStore.executeFlow(typedFetch)

            call.respondBytesWriter(ContentType.parse(RemoteStoreProtocol.streamContentType)) {
                updates.collect { update ->
                    val response = UpdatesResponse(fetchRequest.dataModel, listOf(update))
                    val responseContext = RequestContext(requestContext.definitionsContext, dataModel = fetchRequest.dataModel)
                    val responseBytes = RemoteStoreCodec.encode(UpdatesResponse.Serializer, response, responseContext)
                    writeFully(RemoteStoreCodec.lengthPrefix(responseBytes.size))
                    writeFully(responseBytes)
                    flush()
                }
            }
        }

        post(RemoteStoreProtocol.processUpdatePath) {
            val requestBytes = call.receiveChannel().readRemaining().readByteArray()
            val requestContext = createRequestContext(dataStore)
            val updateRequest = RemoteStoreCodec.decode(UpdateResponse.Serializer, requestBytes, requestContext)
            val response = dataStore.processUpdate(updateRequest)
            val responseContext = RequestContext(requestContext.definitionsContext, dataModel = updateRequest.dataModel)
            val remoteResponse = RemoteProcessResponse(response.version, response.result)
            val responseBytes = RemoteStoreCodec.encode(RemoteProcessResponse.Serializer, remoteResponse, responseContext)
            call.respondBytes(responseBytes, ContentType.parse(RemoteStoreProtocol.contentType))
        }
    }
}

private fun resolveRequest(rawRequest: Any): IsTransportableRequest<*> = when (rawRequest) {
    is IsTransportableRequest<*> -> rawRequest
    is ObjectValues<*, *> -> rawRequest.toDataObject() as IsTransportableRequest<*>
    else -> throw IllegalStateException("Unsupported request payload ${rawRequest::class.simpleName}")
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
