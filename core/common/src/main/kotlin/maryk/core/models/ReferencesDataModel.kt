package maryk.core.models

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.ValueMap
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.filters.Exists
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** For data models which contains only reference pairs */
internal abstract class ReferencesDataModel<DO: Any, P: ReferencesPropertyDefinitions<DO>>(
    properties: P
) : AbstractDataModel<DO, P, DataModelPropertyContext, DataModelPropertyContext>(properties){
    override fun writeJson(map: ValueMap<DO, P>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
        val references = map { references } ?: throw ParseException("References are missing from ReferencesDataModel")

        writer.writeJsonReferences(
            references,
            context
        )
    }

    protected fun IsJsonLikeWriter.writeJsonReferences(
        references: List<IsPropertyReference<*, *>>,
        context: DataModelPropertyContext?
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

    override fun readJson(reader: IsJsonLikeReader, context: DataModelPropertyContext?): ValueMap<DO, P> {
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
                (currentToken as JsonToken.Value<String>).let {
                    mapOf(
                        Exists.Properties.references.index to listOf(
                            Exists.Properties.references.definition.valueDefinition.fromString(currentToken.value, context)
                        )
                    )
                }
            }
            is JsonToken.StartArray -> {
                mapOf(
                    Exists.Properties.references.index to Exists.Properties.references.readJson(reader, context)
                )
            }
            else -> throw ParseException("Expected a list or a single property reference in Exists filter")
        }

        return this.map {
            valueMap
        }
    }
}

internal abstract class ReferencesPropertyDefinitions<DO: Any> : PropertyDefinitions<DO>() {
    abstract val references: ListPropertyDefinitionWrapper<IsPropertyReference<*, *>, IsPropertyReference<*, *>, DataModelPropertyContext, DO>

    internal fun addReferenceListPropertyDefinition(getter: (DO) -> List<IsPropertyReference<*, *>>) =
        this.add(0, "references",
            ListDefinition(
                valueDefinition = ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = {
                        it?.dataModel?.properties ?: throw ContextNotFoundException()
                    }
                )
            ),
            getter
        )
}
