package maryk.core.query.filters

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Checks if [references] exist on DataModel */
data class Exists internal constructor(
    val references: List<IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>>>
) : IsFilter {
    override val filterType = FilterType.Exists

    @Suppress("UNCHECKED_CAST")
    constructor(vararg reference: IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>>): this(reference.toList())

    internal object Properties : PropertyDefinitions<Exists>() {
        val references = add(0, "references",
            ListDefinition(
                valueDefinition = ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = {
                        it?.dataModel?.properties ?: throw ContextNotFoundException()
                    }
                )
            ),
            Exists::references
        )
    }

    internal companion object: QueryDataModel<Exists>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = Exists(
            references = map(0)
        )

        override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            @Suppress("UNCHECKED_CAST")
            val references = map[0] as List<IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>>>

            writer.writeJsonReferences(references, context)
        }

        override fun writeJson(obj: Exists, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            val references = obj.references

            writer.writeJsonReferences(references, context)
        }

        private fun IsJsonLikeWriter.writeJsonReferences(
            references: List<IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>>>,
            context: DataModelPropertyContext?
        ) {
            if (references.size == 1) {
                Properties.references.definition.valueDefinition.writeJsonValue(references[0], this, context)
            } else {
                writeStartArray()
                for (it in references) {
                    Properties.references.definition.valueDefinition.writeJsonValue(it, this, context)
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
                            Properties.references.index to listOf(
                                Properties.references.definition.valueDefinition.fromString(currentToken.value, context)
                            )
                        )
                    }
                }
                is JsonToken.StartArray -> {
                    return mapOf(
                        Properties.references.index to Properties.references.readJson(reader, context)
                    )
                }
                else -> throw ParseException("Expected a list or a single property reference in Exists filter")
            }
        }
    }
}
