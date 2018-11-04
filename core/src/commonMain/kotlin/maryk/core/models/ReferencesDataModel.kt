package maryk.core.models

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.ObjectValues
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.filters.Exists
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** For data models which contains only reference pairs */
abstract class ReferencesDataModel<DO: Any, P: ReferencesObjectPropertyDefinitions<DO>>(
    properties: P
) : AbstractObjectDataModel<DO, P, RequestContext, RequestContext>(properties){
    protected fun IsJsonLikeWriter.writeJsonReferences(
        references: List<AnyPropertyReference>,
        context: RequestContext?
    ) {
        if (references.size == 1) {
            Exists.Properties.references.definition.valueDefinition.writeJsonValue(references[0], this, context)
        } else {
            writeStartArray()
            for (it in references) {
                Exists.Properties.references.definition.valueDefinition.writeJsonValue(it, this, context)
            }
            writeEndArray()
        }
    }

    override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<DO, P> {
        var currentToken = reader.currentToken

        if (currentToken == JsonToken.StartDocument){
            currentToken = reader.nextToken()

            if (currentToken is JsonToken.Suspended) {
                currentToken = currentToken.lastToken
            }
        }

        val valueMap = when (currentToken) {
            is JsonToken.Value<*> -> {
                @Suppress("UNCHECKED_CAST")
                mapOf(
                    Exists.Properties.references.index to listOf(
                        Exists.Properties.references.definition.valueDefinition.fromString(currentToken.value as String, context)
                    )
                )
            }
            is JsonToken.StartArray -> {
                mapOf(
                    Exists.Properties.references.index to Exists.Properties.references.readJson(reader, context)
                )
            }
            else -> throw ParseException("Expected a list or a single property reference in Exists filter")
        }

        return this.map(context) {
            valueMap
        }
    }
}

abstract class ReferencesObjectPropertyDefinitions<DO: Any> : ObjectPropertyDefinitions<DO>() {
    abstract val references: ListPropertyDefinitionWrapper<AnyPropertyReference, AnyPropertyReference, RequestContext, DO>

    internal fun addReferenceListPropertyDefinition(getter: (DO) -> List<AnyPropertyReference>) =
        this.add(1, "references",
            ListDefinition(
                valueDefinition = ContextualPropertyReferenceDefinition<RequestContext>(
                    contextualResolver = {
                        it?.dataModel?.properties as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException()
                    }
                )
            ),
            getter
        )
}