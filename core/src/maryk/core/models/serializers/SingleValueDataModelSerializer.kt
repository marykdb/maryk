package maryk.core.models.serializers

import maryk.core.models.IsObjectDataModel
import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

open class SingleValueDataModelSerializer<T : Any, TO : Any, DO : Any, P : IsObjectDataModel<DO>, CX : IsPropertyContext>(
    model: P,
    singlePropertyDefinitionGetter: () -> IsDefinitionWrapper<T, out TO, CX, DO>
) : ObjectDataModelSerializer<DO, P, CX, CX>(model) {
    private val singlePropertyDefinition by lazy(singlePropertyDefinitionGetter)

    override fun writeJson(values: ObjectValues<DO, P>, writer: IsJsonLikeWriter, context: CX?) {
        val value = values.original { singlePropertyDefinition }
            ?: throw ParseException("Missing ${singlePropertyDefinition.name} value")
        writeJsonValue(value, writer, context)
    }

    override fun writeObjectAsJson(
        obj: DO,
        writer: IsJsonLikeWriter,
        context: CX?,
        skip: List<IsDefinitionWrapper<*, *, *, DO>>?
    ) {
        val value = singlePropertyDefinition.getPropertyAndSerialize(obj, context)
            ?: throw ParseException("Missing ${singlePropertyDefinition.name} value")
        writeJsonValue(value, writer, context)
    }

    open fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: CX?) {
        singlePropertyDefinition.writeJsonValue(value, writer, context)
        singlePropertyDefinition.capture(context, value)
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?): ObjectValues<DO, P> {
        if (reader.currentToken == JsonToken.StartDocument) {
            reader.nextToken()
        }

        val value = readJsonValue(reader, context)

        @Suppress("UNCHECKED_CAST")
        return (model as TypedObjectDataModel<DO, P, *, *>).create {
            singlePropertyDefinition -= value
        }
    }

    open fun readJsonValue(reader: IsJsonLikeReader, context: CX?) =
        singlePropertyDefinition.readJson(reader, context).also {
            singlePropertyDefinition.capture(context, it)
        }
}
