package maryk.core.models

import maryk.core.exceptions.SerializationException
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IllegalJsonOperation
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/**
 * Class for DataModels which contains a list of only reference pairs of type [R]
 * Contains specific Serializer overrides to create a nicer looking output.
 */
abstract class ReferenceValuePairsDataModel<DO: Any, DM: ReferenceValuePairsDataModel<DO, DM, R, T, TO>, R: DefinedByReference<*>, T : Any, TO : Any>(
    pairGetter: (DO) -> List<R>,
    val pairModel: ReferenceValuePairDataModel<R, *, *, *, out IsDefinitionWrapper<T, TO, RequestContext, R>>,
): TypedObjectDataModel<DO, DM, RequestContext, RequestContext>() {
    val referenceValuePairs by list(
        index = 1u,
        getter = pairGetter,
        valueDefinition = EmbeddedObjectDefinition(
            dataModel = { pairModel }
        )
    )

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Serializer = object: ObjectDataModelSerializer<DO, DM, RequestContext, RequestContext>(this as DM) {
        override fun writeJson(
            values: ObjectValues<DO, DM>,
            writer: IsJsonLikeWriter,
            context: RequestContext?
        ) {
            val referenceTypePairs = values { referenceValuePairs } ?: throw ParseException("ranges was not set on Range")
            writer.writeJsonTypePairs(referenceTypePairs, context)
        }

        override fun writeObjectAsJson(
            obj: DO,
            writer: IsJsonLikeWriter,
            context: RequestContext?,
            skip: List<IsDefinitionWrapper<*, *, *, DO>>?
        ) {
            @Suppress("UNCHECKED_CAST")
            val pairs = model[1u]?.getter?.invoke(obj) as? List<R>
                ?: throw SerializationException("No pairs defined on $obj")

            writer.writeJsonTypePairs(pairs, context)
        }

        private fun IsJsonLikeWriter.writeJsonTypePairs(
            referencePairs: List<R>,
            context: RequestContext?
        ) {
            writeStartObject()
            for (it in referencePairs) {
                writeFieldName(
                    pairModel.reference.definition.asString(it.reference)
                )
                pairModel.reference.capture(context, it.reference)

                pairModel.value.writeJsonValue(
                    pairModel.value.getPropertyAndSerialize(it, context)
                        ?: throw SerializationException("No pair value defined on $it"),
                    this,
                    context
                )
            }
            writeEndObject()
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<DO, DM> {
            if (reader.currentToken == JsonToken.StartDocument) {
                reader.nextToken()
            }

            if (reader.currentToken !is JsonToken.StartObject) {
                throw IllegalJsonOperation("Expected object at start of JSON")
            }

            val listOfTypePairs = mutableListOf<R>()
            reader.nextToken()

            walker@ do {
                val token = reader.currentToken
                when (token) {
                    is JsonToken.FieldName -> {
                        val refName = token.value ?: throw ParseException("Empty field name not allowed in JSON")

                        val reference = pairModel.reference.definition.fromString(refName, context)
                        pairModel.reference.capture(context, reference)

                        reader.nextToken()

                        @Suppress("UNCHECKED_CAST")
                        val value = pairModel.value.readJson(reader, context) as TO?

                        listOfTypePairs.add(
                            model.pairModel.values {
                                mapNonNulls(
                                    this@ReferenceValuePairsDataModel.pairModel.reference with reference,
                                    this@ReferenceValuePairsDataModel.pairModel.value with value
                                )
                            }.toDataObject()
                        )
                    }
                    else -> break@walker
                }
                reader.nextToken()
            } while (token !is JsonToken.Stopped)

            return model.values(context) {
                mapNonNulls(
                    model.referenceValuePairs withSerializable listOfTypePairs
                )
            }
        }
    }
}
