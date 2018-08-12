package maryk.core.query.requests

import maryk.core.models.QueryDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

data class CollectRequest(
    val name: String,
    val request: IsRequest
) : IsRequest {
    override val requestType = RequestType.Collect

    object Properties: ObjectPropertyDefinitions<CollectRequest>() {
        val name = add(1, "name", StringDefinition(), CollectRequest::name)

        @Suppress("UNCHECKED_CAST")
        val request = add(2, "request",
            MultiTypeDefinition(
                typeEnum = RequestType,
                definitionMap = mapOfRequestTypeEmbeddedObjectDefinitions
            ) as IsSerializableFlexBytesEncodable<TypedValue<RequestType, IsRequest>, RequestContext>,
            getter = CollectRequest::request,
            toSerializable = { request, _ ->
                request?.let {
                    TypedValue(request.requestType, request)
                }
            },
            fromSerializable = { request ->
                request?.value
            }
        )
    }

    companion object: QueryDataModel<CollectRequest, CollectRequest.Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<CollectRequest, CollectRequest.Properties>) = CollectRequest(
            name = map(1),
            request = map(2)
        )

        override fun writeJson(obj: CollectRequest, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeStartObject()
            writer.writeFieldName(obj.name)
            val typedRequest = Properties.request.toSerializable?.invoke(obj.request, context)!!
            Properties.request.definition.writeJsonValue(typedRequest, writer, context)
            writer.writeEndObject()
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<CollectRequest, CollectRequest.Properties> {
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

            return this.map {
                mapNonNulls(
                    this.name with name,
                    this.request with request
                )
            }
        }
    }
}
