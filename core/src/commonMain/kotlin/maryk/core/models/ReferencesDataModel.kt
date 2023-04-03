package maryk.core.models

import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItems
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

abstract class ReferencesDataModel<DO: Any, DM: ReferencesDataModel<DO, DM>>(
    referencesGetter: (DO) -> List<AnyPropertyReference>,
) : ObjectDataModel<DO, DM, RequestContext, RequestContext>() {
    abstract val references: ListDefinitionWrapper<AnyPropertyReference, AnyPropertyReference, RequestContext, DO>

    abstract override fun invoke(values: ObjectValues<DO, DM>): DO

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Serializer = object: ObjectDataModelSerializer<DO, DM, RequestContext, RequestContext>(
        this as DM
    ) {
        override fun writeObjectAsJson(
            obj: DO,
            writer: IsJsonLikeWriter,
            context: RequestContext?,
            skip: List<IsDefinitionWrapper<*, *, *, DO>>?
        ) {
            writer.writeJsonReferences(referencesGetter(obj), context)
        }

        protected fun IsJsonLikeWriter.writeJsonReferences(
            references: List<AnyPropertyReference>,
            context: RequestContext?
        ) {
            if (references.size == 1) {
                model.references.definition.valueDefinition.writeJsonValue(references[0], this, context)
            } else {
                writeStartArray()
                for (it in references) {
                    model.references.definition.valueDefinition.writeJsonValue(it, this, context)
                }
                writeEndArray()
            }
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<DO, DM> {
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
                        model.references withNotNull listOf(
                            (model.references.definition.valueDefinition as IsValueDefinition<*, RequestContext>).fromString(
                                currentToken.value as String,
                                context
                            )
                        )
                    )
                }
                is JsonToken.StartArray -> {
                    ValueItems(
                        model.references withNotNull model.references.readJson(reader, context)
                    )
                }
                else -> throw ParseException("Expected a list or a single property reference in Exists filter")
            }

            @Suppress("UNCHECKED_CAST")
            return values(context) {
                valueMap
            } as ObjectValues<DO, DM>
        }
    }
}
