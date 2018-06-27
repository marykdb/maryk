package maryk.core.models

import maryk.core.objects.ValueMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/**
 * DataModel of type [DO] with [properties] definitions to contain
 * query actions so they can be validated and transported
 */
internal abstract class SimpleFilterDataModel<DO: Any, P: PropertyDefinitions<DO>>(
    properties: P
) : AbstractDataModel<DO, P, DataModelPropertyContext, DataModelPropertyContext>(properties){

    override fun writeJson(map: ValueMap<DO, P>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
        @Suppress("UNCHECKED_CAST")
        writer.writeJsonValues(
            this.properties.getDefinition(0) as PropertyDefinitionWrapper<IsPropertyReference<*, *>, IsPropertyReference<*, *>, DataModelPropertyContext, ContextualPropertyReferenceDefinition<DataModelPropertyContext>, Any>,
            map[0] as IsPropertyReference<*, *>,
            this.properties.getDefinition(1) as PropertyDefinitionWrapper<Any, *, IsPropertyContext, *, *>,
            map[1] as Any,
            context
        )
    }

    override fun walkJsonToRead(
        reader: IsJsonLikeReader,
        valueMap: MutableMap<Int, Any>,
        context: DataModelPropertyContext?
    ) {
        val referenceProperty =
            this.properties.getDefinition(0)
        val referencePropertyDefinition = referenceProperty?.definition as? IsValueDefinition<*, DataModelPropertyContext>
            ?: throw Exception("No first property found")

        (reader.currentToken as? JsonToken.FieldName)?.value?.let {
            valueMap[referenceProperty.index] =
                    referencePropertyDefinition.fromString(it, context)
        } ?: throw ParseException("Expected a field name for reference on filter")

        reader.nextToken()

        val valueProperty =
            this.properties.getDefinition(1) ?: throw Exception("No property for value found")

        valueMap[valueProperty.index] =
                valueProperty.readJson(reader, context)

        if(reader.nextToken() !== JsonToken.EndObject) {
            throw ParseException("Expected only 1 value on map for filter")
        }
    }

    private fun <T: Any, CX: IsPropertyContext> IsJsonLikeWriter.writeJsonValues(
        referenceProperty: PropertyDefinitionWrapper<IsPropertyReference<*, *>, IsPropertyReference<*, *>, DataModelPropertyContext, ContextualPropertyReferenceDefinition<DataModelPropertyContext>, *>,
        reference: IsPropertyReference<*, *>,
        valueProperty: IsPropertyDefinitionWrapper<T, *, CX, *>,
        value: T,
        context: DataModelPropertyContext?
    ) {
        writeStartObject()
        writeFieldName(
            referenceProperty.definition.asString(reference, context)
        )

        referenceProperty.capture(context, reference)

        @Suppress("UNCHECKED_CAST")
        valueProperty.writeJsonValue(value, this, context as CX?)

        writeEndObject()
    }
}
