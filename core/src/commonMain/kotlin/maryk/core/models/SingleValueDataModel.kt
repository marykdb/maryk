package maryk.core.models

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

typealias QuerySingleTypedValueDataModel<T, DO, P, CX> = QuerySingleValueDataModel<T, T, DO, P, CX>

/**
 * ObjectDataModel of type [DO] with [properties] definitions with a single property to contain
 * query actions so they can be validated and transported.
 *
 * In JSON/YAML this model is represented as just that property.
 */
abstract class QuerySingleValueDataModel<T: Any, TO: Any, DO: Any, P: ObjectPropertyDefinitions<DO>, CX: IsPropertyContext>(
    properties: P,
    private val singlePropertyDefinition: IsPropertyDefinitionWrapper<T, TO, CX, DO>
) : AbstractObjectDataModel<DO, P, CX, CX>(properties) {
    override fun writeJson(values: ObjectValues<DO, P>, writer: IsJsonLikeWriter, context: CX?) {
        val value = values.original { singlePropertyDefinition } ?: throw ParseException("Missing ${singlePropertyDefinition.name} value")
        writeJsonValue(value, writer, context)
    }

    override fun writeJson(obj: DO, writer: IsJsonLikeWriter, context: CX?) {
        val value = singlePropertyDefinition.getPropertyAndSerialize(obj, context) ?: throw ParseException("Missing ${singlePropertyDefinition.name} value")
        writeJsonValue(value, writer, context)
    }

    open fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: CX?) {
        singlePropertyDefinition.writeJsonValue(value, writer, context)
        singlePropertyDefinition.capture(context, value)
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?): ObjectValues<DO, P> {
        if (reader.currentToken == JsonToken.StartDocument){
            reader.nextToken()
        }

        val value = readJsonValue(reader, context)

        return this.values(context as? RequestContext) {
            mapNonNulls(
                singlePropertyDefinition withSerializable value
            )
        }
    }

    open fun readJsonValue(reader: IsJsonLikeReader, context: CX?) =
        singlePropertyDefinition.readJson(reader, context).also {
            singlePropertyDefinition.capture(context, it)
        }
}
