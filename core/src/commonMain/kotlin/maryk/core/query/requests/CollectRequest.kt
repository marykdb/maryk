package maryk.core.query.requests

import maryk.core.models.QueryDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.query.responses.IsResponse
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

typealias AnyCollectRequest = CollectRequest<*, *>

data class CollectRequest<RQ: IsRequest<RP>, RP: IsResponse>(
    val name: String,
    val request: RQ
) : IsRequest<RP> {
    override val requestType = RequestType.Collect
    override val responseModel = request.responseModel

    object Properties: ObjectPropertyDefinitions<AnyCollectRequest>() {
        val name = add(1, "name", StringDefinition(), AnyCollectRequest::name)

        val request = add(2, "request",
            MultiTypeDefinition(
                typeEnum = RequestType,
                definitionMap = mapOfRequestTypeEmbeddedObjectDefinitions
            ),
            getter = AnyCollectRequest::request,
            toSerializable = { request, _ ->
                request?.let {
                    TypedValue(request.requestType, request)
                }
            },
            fromSerializable = { request ->
                request?.value as IsRequest<*>?
            }
        )
    }

    companion object: QueryDataModel<AnyCollectRequest, CollectRequest.Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<AnyCollectRequest, CollectRequest.Properties>) = CollectRequest<IsRequest<IsResponse>, IsResponse>(
            name = map(1),
            request = map(2)
        )

        override fun writeJson(obj: AnyCollectRequest, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeStartObject()
            writer.writeFieldName(obj.name)
            val typedRequest = Properties.request.toSerializable?.invoke(obj.request, context)!!
            Properties.request.definition.writeJsonValue(typedRequest, writer, context)
            writer.writeEndObject()
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<AnyCollectRequest, CollectRequest.Properties> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            if (reader.currentToken !is JsonToken.StartObject) {
                throw ParseException("JSON value should be an Object")
            }

            val currentToken = reader.nextToken()

            val name = if (currentToken is JsonToken.FieldName) {
                currentToken.value
            } else throw ParseException("Expected a name in a CollectRequest")

            reader.nextToken()
            val request = Properties.request.readJson(reader, context)

            reader.nextToken() // read past end object

            return this.map(context) {
                mapNonNulls(
                    this.name withSerializable name,
                    this.request withSerializable request
                )
            }
        }
    }
}
