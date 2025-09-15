package maryk.core.models.serializers

import maryk.core.models.IsObjectDataModel
import maryk.core.models.QueryModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItems
import maryk.json.IllegalJsonOperation
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

open class ReferenceMappedDataModelSerializer<DO : Any, CDO : DefinedByReference<*>, DM : IsObjectDataModel<DO>, CP : IsObjectDataModel<CDO>>(
    model: DM,
    private val containedDataModel: QueryModel<CDO, CP>,
    private val referenceProperty: ContextualDefinitionWrapper<AnyPropertyReference, AnyPropertyReference, RequestContext, ContextualPropertyReferenceDefinition<RequestContext>, CDO>
) : ObjectDataModelSerializer<DO, DM, RequestContext, RequestContext>(model) {

    /** Write a values to [writer] with references mapped to the internal model for [items] within [context] */
    internal fun writeReferenceValueMap(
        writer: IsJsonLikeWriter,
        items: List<CDO>,
        context: RequestContext?
    ) {
        writer.writeStartObject()
        for (item in items) {
            writer.writeFieldName(
                referenceProperty.definition.asString(item.reference, context)
            )
            referenceProperty.capture(context, item.reference)

            writer.writeStartObject()
            for (propertyWrapper in this.containedDataModel) {
                if (propertyWrapper != this.referenceProperty) {
                    writer.writeField(item, propertyWrapper, context)
                }
            }

            writer.writeEndObject()
        }
        writer.writeEndObject()
    }

    private fun <T : Any, CX : IsPropertyContext> IsJsonLikeWriter.writeField(
        dataObject: CDO,
        definitionWrapper: IsDefinitionWrapper<T, T, CX, CDO>,
        context: CX?
    ) {
        definitionWrapper.getPropertyAndSerialize(dataObject, context)?.let {
            writeFieldName(definitionWrapper.name)
            definitionWrapper.writeJsonValue(it, this, context)
        }
    }

    override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<DO, DM> {
        if (reader.currentToken == JsonToken.StartDocument) {
            reader.nextToken()
        }

        if (reader.currentToken !is JsonToken.StartObject) {
            throw IllegalJsonOperation("Expected object at start of JSON")
        }

        val items = mutableListOf<CDO>()

        reader.nextToken()

        walker@ do {
            val token = reader.currentToken
            when (token) {
                is JsonToken.FieldName -> {
                    val value = token.value ?: throw ParseException("Empty field name not allowed in JSON")

                    val valueMap = MutableValueItems()

                    valueMap[this.referenceProperty.index] =
                        this.referenceProperty.definition.fromString(value, context).also {
                            this.referenceProperty.capture(context, it)
                        }

                    reader.nextToken()

                    if (reader.currentToken != JsonToken.NullValue) {
                        if (reader.currentToken !is JsonToken.StartObject) {
                            throw IllegalJsonOperation("Expected object below reference")
                        }
                        reader.nextToken()

                        this.containedDataModel.Serializer.walkJsonToRead(reader, valueMap, context)
                    }

                    val dataObjectMap = ObjectValues(this.containedDataModel, valueMap, context)
                    items.add(
                        dataObjectMap.toDataObject()
                    )
                }
                else -> break@walker
            }
            reader.nextToken()
        } while (true)

        return ObjectValues(
            model,
            ValueItems(
                referenceProperty asValueItem items
            )
        )
    }
}
