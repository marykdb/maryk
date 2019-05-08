package maryk.core.query.requests

import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.StringDefinition
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

data class CollectRequest<RQ : IsRequest<RP>, RP : IsResponse>(
    val name: String,
    val request: RQ
) : IsRequest<RP> {
    override val requestType = Collect
    override val responseModel = request.responseModel

    object Properties : ObjectPropertyDefinitions<AnyCollectRequest>() {
        val name = add(1u, "name", StringDefinition(), AnyCollectRequest::name)

        // It transmits any instead of IsRequest so the ObjectValues can also be transmitted
        val request = add(2u, "request",
            MultiTypeDefinition(
                typeEnum = RequestType,
                definitionMap = mapOfRequestTypeEmbeddedObjectDefinitions
            ),
            getter = AnyCollectRequest::request,
            toSerializable = { request: Any?, _ ->
                request?.let {
                    TypedValue((request as IsRequest<*>).requestType, request)
                }
            },
            fromSerializable = { request: TypedValue<RequestType, Any>? ->
                request?.value
            }
        )
    }

    companion object : QueryDataModel<AnyCollectRequest, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<AnyCollectRequest, Properties>) =
            CollectRequest<IsRequest<IsResponse>, IsResponse>(
                name = values(1u),
                request = values(2u)
            )

        override fun writeJson(obj: AnyCollectRequest, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeStartObject()
            writer.writeFieldName(obj.name)
            val typedRequest = Properties.request.toSerializable?.invoke(obj.request, context)!!
            Properties.request.definition.writeJsonValue(typedRequest, writer, context)
            writer.writeEndObject()
        }

        override fun readJson(
            reader: IsJsonLikeReader,
            context: RequestContext?
        ): ObjectValues<AnyCollectRequest, Properties> {
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
            val request = Properties.request.readJson(reader, context)

            reader.nextToken() // read past end object

            return this.values(context) {
                mapNonNulls(
                    this.name withSerializable name,
                    this.request withSerializable request
                )
            }
        }
    }
}
