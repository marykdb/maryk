package maryk.core.models

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItems
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.Suspended
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException

/** For data models which contains only reference pairs */
abstract class ReferencesDataModel<DO : Any, P : ReferencesObjectPropertyDefinitions<DO>>(
    properties: P
) : AbstractObjectDataModel<DO, P, RequestContext, RequestContext>(properties) {
    protected fun IsJsonLikeWriter.writeJsonReferences(
        references: List<AnyPropertyReference>,
        context: RequestContext?
    ) {
        if (references.size == 1) {
            properties.references.definition.valueDefinition.writeJsonValue(references[0], this, context)
        } else {
            writeStartArray()
            for (it in references) {
                properties.references.definition.valueDefinition.writeJsonValue(it, this, context)
            }
            writeEndArray()
        }
    }

    override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<DO, P> {
        var currentToken = reader.currentToken

        if (currentToken == StartDocument) {
            currentToken = reader.nextToken()

            if (currentToken is Suspended) {
                currentToken = currentToken.lastToken
            }
        }

        val valueMap = when (currentToken) {
            is Value<*> -> {
                ValueItems(
                    properties.references withNotNull listOf(
                        (properties.references.definition.valueDefinition as IsValueDefinition<*, RequestContext>).fromString(
                            currentToken.value as String,
                            context
                        )
                    )
                )
            }
            is StartArray -> {
                ValueItems(
                    properties.references withNotNull properties.references.readJson(reader, context)
                )
            }
            else -> throw ParseException("Expected a list or a single property reference in Exists filter")
        }

        return this.values(context) {
            valueMap
        }
    }
}

abstract class ReferencesObjectPropertyDefinitions<DO : Any> : ObjectPropertyDefinitions<DO>() {
    abstract val references: ListDefinitionWrapper<AnyPropertyReference, AnyPropertyReference, RequestContext, DO>

    internal fun addReferenceListPropertyDefinition(getter: (DO) -> List<AnyPropertyReference>) =
        this.add(1u, "references",
            ListDefinition(
                valueDefinition = ContextualPropertyReferenceDefinition<RequestContext>(
                    contextualResolver = {
                        it?.dataModel?.properties as? AbstractPropertyDefinitions<*>?
                            ?: throw ContextNotFoundException()
                    }
                )
            ),
            getter
        )
}
