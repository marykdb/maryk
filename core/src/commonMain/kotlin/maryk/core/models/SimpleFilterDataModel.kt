package maryk.core.models

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.RequestContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/**
 * ObjectDataModel of type [DO] with [properties] definitions to contain
 * query actions so they can be validated and transported
 */
abstract class SimpleFilterDataModel<DO: Any, P: ObjectPropertyDefinitions<DO>>(
    properties: P
) : AbstractObjectDataModel<DO, P, RequestContext, RequestContext>(properties){
    override fun walkJsonToRead(
        reader: IsJsonLikeReader,
        valueMap: MutableMap<Int, Any>,
        context: RequestContext?
    ) {
        val referenceProperty =
            this.properties[0]
        val referencePropertyDefinition = referenceProperty?.definition as? IsValueDefinition<*, RequestContext>
            ?: throw Exception("No first property found")

        (reader.currentToken as? JsonToken.FieldName)?.value?.let {
            valueMap[referenceProperty.index] =
                    referencePropertyDefinition.fromString(it, context)
        } ?: throw ParseException("Expected a field name for reference on filter")

        reader.nextToken()

        val valueProperty =
            this.properties[1] ?: throw Exception("No property for value found")

        valueMap[valueProperty.index] =
                valueProperty.readJson(reader, context)

        if(reader.nextToken() !== JsonToken.EndObject) {
            throw ParseException("Expected only 1 value on map for filter")
        }
    }

    private fun <T: Any, CX: IsPropertyContext> IsJsonLikeWriter.writeJsonValues(
        referenceProperty: PropertyDefinitionWrapper<AnyPropertyReference, AnyPropertyReference, RequestContext, ContextualPropertyReferenceDefinition<RequestContext>, *>,
        reference: AnyPropertyReference,
        valueProperty: IsPropertyDefinitionWrapper<T, *, CX, *>,
        value: T,
        context: RequestContext?
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