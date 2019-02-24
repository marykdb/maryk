package maryk.core.models

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.ContextualPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
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

/** For data models which contains only reference pairs */
abstract class ReferenceMappedDataModel<DO : Any, CDO : DefinedByReference<*>, P : ObjectPropertyDefinitions<DO>, CP : ObjectPropertyDefinitions<CDO>>(
    properties: P,
    private val containedDataModel: QueryDataModel<CDO, CP>,
    private val referenceProperty: ContextualPropertyDefinitionWrapper<AnyPropertyReference, AnyPropertyReference, RequestContext, ContextualPropertyReferenceDefinition<RequestContext>, CDO>
) : QueryDataModel<DO, P>(properties) {

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
            for (propertyWrapper in this.containedDataModel.properties) {
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
        definitionWrapper: IsPropertyDefinitionWrapper<T, T, CX, CDO>,
        context: CX?
    ) {
        definitionWrapper.getter(dataObject)?.let {
            writeFieldName(definitionWrapper.name)
            definitionWrapper.writeJsonValue(it, this, context)
        }
    }

    override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<DO, P> {
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

                        this.containedDataModel.walkJsonToRead(reader, valueMap, context)
                    }

                    val dataObjectMap = this.containedDataModel.values(context) {
                        valueMap
                    }
                    items.add(
                        dataObjectMap.toDataObject()
                    )
                }
                else -> break@walker
            }
            reader.nextToken()
        } while (token !is JsonToken.Stopped)

        return this.values(context) {
            ValueItems(
                referenceProperty withNotNull items
            )
        }
    }
}
