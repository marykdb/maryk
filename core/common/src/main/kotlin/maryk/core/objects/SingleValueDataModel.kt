package maryk.core.objects

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/**
 * DataModel of type [DO] with [properties] definitions with a single property to contain
 * query actions so they can be validated and transported.
 *
 * In JSON/YAML this model is represented as just that property.
 */
internal abstract class QuerySingleValueDataModel<T: Any, DO: Any, CX: IsPropertyContext>(
    properties: PropertyDefinitions<DO>,
    private val singlePropertyDefinition: IsPropertyDefinitionWrapper<T, *, CX, DO>
) : AbstractDataModel<DO, PropertyDefinitions<DO>, CX, CX>(properties) {
    override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: CX?) {
        @Suppress("UNCHECKED_CAST")
        val value = map[singlePropertyDefinition.index] as T? ?: throw ParseException("Missing requests in Requests")

        singlePropertyDefinition.writeJsonValue(value, writer, context)
        singlePropertyDefinition.capture(context, value)
    }

    override fun writeJson(obj: DO, writer: IsJsonLikeWriter, context: CX?) {
        val value = singlePropertyDefinition.getPropertyAndSerialize(obj, context) ?: throw ParseException("Missing ${singlePropertyDefinition.name} value")
        singlePropertyDefinition.writeJsonValue(value, writer, context)
        singlePropertyDefinition.capture(context, value)
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?): Map<Int, Any> {
        if (reader.currentToken == JsonToken.StartDocument){
            reader.nextToken()
        }

        val value = singlePropertyDefinition.readJson(reader, context)
        singlePropertyDefinition.capture(context, value)

        return mapOf(
            singlePropertyDefinition.index to value
        )
    }
}
