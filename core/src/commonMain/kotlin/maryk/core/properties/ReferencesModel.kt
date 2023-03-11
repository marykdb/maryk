package maryk.core.properties

import maryk.core.models.QueryDataModel
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItems
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

abstract class ReferencesModel<DO: Any, P: ReferencesModel<DO, P>>(
    referencesGetter: (DO) -> List<AnyPropertyReference>,
)
    : ObjectPropertyDefinitions<DO>(), IsObjectPropertyDefinitions<DO>, IsInternalModel<DO, P, RequestContext, RequestContext> {
    abstract val references: ListDefinitionWrapper<AnyPropertyReference, AnyPropertyReference, RequestContext, DO>

    abstract fun invoke(values: ObjectValues<DO, P>): DO

    @Suppress("UNCHECKED_CAST")
    override val Model = object: QueryDataModel<DO, P>(
        this@ReferencesModel as P,
    ) {
        override fun invoke(values: ObjectValues<DO, P>): DO =
            this@ReferencesModel.invoke(values)

        override fun writeJson(obj: DO, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonReferences(referencesGetter(obj), context)
        }

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

            if (currentToken == JsonToken.StartDocument) {
                currentToken = reader.nextToken()

                if (currentToken is JsonToken.Suspended) {
                    currentToken = currentToken.lastToken
                }
            }

            val valueMap = when (currentToken) {
                is JsonToken.Value<*> -> {
                    ValueItems(
                        properties.references withNotNull listOf(
                            (properties.references.definition.valueDefinition as IsValueDefinition<*, RequestContext>).fromString(
                                currentToken.value as String,
                                context
                            )
                        )
                    )
                }
                is JsonToken.StartArray -> {
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
}
