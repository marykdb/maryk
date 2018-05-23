package maryk.core.objects

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.filters.Exists
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** For data models which contains only reference pairs */
internal abstract class ReferencesDataModel<DO: Any>(
    properties: ReferencesPropertyDefinitions<DO>
) : AbstractDataModel<DO, ReferencesPropertyDefinitions<DO>, DataModelPropertyContext, DataModelPropertyContext>(properties){
    override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
        @Suppress("UNCHECKED_CAST")
        val references = map[0] as List<IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>>>

        writer.writeJsonReferences(references, context)
    }

    protected fun IsJsonLikeWriter.writeJsonReferences(
        references: List<IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>>>,
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

    override fun readJson(reader: IsJsonLikeReader, context: DataModelPropertyContext?): Map<Int, Any> {
        var currentToken = reader.currentToken

        if (currentToken == JsonToken.StartDocument){
            currentToken = reader.nextToken()

            if (currentToken is JsonToken.Suspended) {
                currentToken = currentToken.lastToken
            }
        }

        when (currentToken) {
            is JsonToken.Value<*> -> {
                @Suppress("UNCHECKED_CAST")
                (currentToken as JsonToken.Value<String>).let {
                    return mapOf(
                        Exists.Properties.references.index to listOf(
                            Exists.Properties.references.definition.valueDefinition.fromString(currentToken.value, context)
                        )
                    )
                }
            }
            is JsonToken.StartArray -> {
                return mapOf(
                    Exists.Properties.references.index to Exists.Properties.references.readJson(reader, context)
                )
            }
            else -> throw ParseException("Expected a list or a single property reference in Exists filter")
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
