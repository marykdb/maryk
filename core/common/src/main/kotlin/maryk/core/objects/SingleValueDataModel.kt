package maryk.core.objects

import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.query.DataModelPropertyContext
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
internal abstract class QuerySingleValueDataModel<T: Any, DO: Any>(
    properties: PropertyDefinitions<DO>,
    private val singlePropertyDefinition: IsPropertyDefinitionWrapper<T, *, DataModelPropertyContext, DO>
) : QueryDataModel<DO>(properties) {
    override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
        @Suppress("UNCHECKED_CAST")
        singlePropertyDefinition.writeJsonValue(
            map[singlePropertyDefinition.index] as T? ?: throw ParseException("Missing requests in Requests"),
            writer,
            context
        )
    }

    override fun writeJson(obj: DO, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
        singlePropertyDefinition.writeJsonValue(
            singlePropertyDefinition.getPropertyAndSerialize(obj) ?: throw ParseException("Missing requests in Requests"),
            writer,
            context
        )
    }

    override fun readJson(reader: IsJsonLikeReader, context: DataModelPropertyContext?): Map<Int, Any> {
        if (reader.currentToken == JsonToken.StartDocument){
            reader.nextToken()
        }

        return mapOf(
            singlePropertyDefinition.index to singlePropertyDefinition.readJson(reader, context)
        )
    }
}
