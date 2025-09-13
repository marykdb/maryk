package maryk.core.query.requests

import maryk.core.models.QueryModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.definitions.internalMultiType
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.query.requests.RequestType.Collect
import maryk.core.query.responses.IsResponse
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.StartObject
import maryk.lib.exceptions.ParseException

typealias AnyCollectRequest = CollectRequest<*, *>

data class CollectRequest<RQ : IsTransportableRequest<RP>, RP : IsResponse>(
    val name: String,
    val request: RQ
) : IsRequest<RP>, IsTransportableRequest<RP> {
    override val requestType = Collect
    override val responseModel = request.responseModel

    companion object : QueryModel<AnyCollectRequest, Companion>() {
        val name by string(1u, AnyCollectRequest::name)

        // It transmits any instead of IsRequest so the ObjectValues can also be transmitted
        val request by internalMultiType(
            2u,
            getter = AnyCollectRequest::request,
            typeEnum = RequestType,
            definitionMap = mapOfRequestTypeEmbeddedObjectDefinitions,
            toSerializable = { request: Any?, _ ->
                request?.let {
                    TypedValue((request as IsTransportableRequest<*>).requestType, request)
                }
            },
            fromSerializable = { request: TypedValue<RequestType, Any>? ->
                request?.value
            }
        )

        override fun invoke(values: ObjectValues<CollectRequest<*, *>, Companion>): CollectRequest<*, *> =
            CollectRequest<IsTransportableRequest<IsResponse>, IsResponse>(
                name = values(1u),
                request = values(2u)
            )

        override val Serializer = object: ObjectDataModelSerializer<CollectRequest<*, *>, Companion, RequestContext, RequestContext>(this) {
            override fun writeObjectAsJson(
                obj: CollectRequest<*, *>,
                writer: IsJsonLikeWriter,
                context: RequestContext?,
                skip: List<IsDefinitionWrapper<*, *, *, CollectRequest<*, *>>>?
            ) {
                writer.writeStartObject()
                writer.writeFieldName(obj.name)
                val typedRequest = request.toSerializable?.invoke(obj.request, context)!!
                request.definition.writeJsonValue(typedRequest, writer, context)
                writer.writeEndObject()
            }

            override fun readJson(
                reader: IsJsonLikeReader,
                context: RequestContext?
            ): ObjectValues<AnyCollectRequest, Companion> {
                if (reader.currentToken == StartDocument) {
                    reader.nextToken()
                }

                if (reader.currentToken !is StartObject) {
                    throw ParseException("JSON value should be an Object")
                }

                val currentToken = reader.nextToken()

                val name = if (currentToken is FieldName) {
                    currentToken.value
                } else throw ParseException("Expected a name in a CollectRequest")

                reader.nextToken()
                val request = request.readJson(reader, context)

                reader.nextToken() // read past end object

                return create(context) {
                    this.name -= name
                    this.request -= request
                }
            }
        }
    }
}
