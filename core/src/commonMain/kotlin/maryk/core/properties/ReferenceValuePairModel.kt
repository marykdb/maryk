package maryk.core.properties

import maryk.core.exceptions.SerializationException
import maryk.core.models.QueryDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceValuePairPropertyDefinitions
import maryk.core.values.ObjectValues
import maryk.json.IllegalJsonOperation
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** For data models which contains only reference pairs of type [R] */
abstract class ReferenceValuePairModel<DO: Any, P: ReferenceValuePairModel<DO, P, R, T, TO>, R: DefinedByReference<*>, T : Any, TO : Any>(
    val pairName: String,
    pairGetter: (DO) -> List<R>,
    val pairModel: QueryDataModel<R, out IsObjectPropertyDefinitions<R>>,
    val pairProperties: ReferenceValuePairPropertyDefinitions<R, T, TO, IsDefinitionWrapper<T, TO, RequestContext, R>>,
): ObjectPropertyDefinitions<DO>(), IsInternalModel<DO, P> {
    val referenceValuePairs by list(
        index = 1u,
        getter = pairGetter,
        valueDefinition = EmbeddedObjectDefinition(
            dataModel = {
                @Suppress("UNCHECKED_CAST")
                pairModel as QueryDataModel<R, ObjectPropertyDefinitions<R>>
            }
        )
    )

    abstract fun invoke(values: ObjectValues<DO, P>): DO

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Model = object: QueryDataModel<DO, P>(
        properties = this@ReferenceValuePairModel as P,
    ) {
        override fun writeJson(
            values: ObjectValues<DO, P>,
            writer: IsJsonLikeWriter,
            context: RequestContext?
        ) {
            val referenceTypePairs = values { referenceValuePairs } ?: throw ParseException("ranges was not set on Range")
            writer.writeJsonTypePairs(referenceTypePairs, context)
        }

        override fun writeJson(obj: DO, writer: IsJsonLikeWriter, context: RequestContext?) {
            @Suppress("UNCHECKED_CAST")
            val pairs = properties[1u]?.getter?.invoke(obj) as? List<R>
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
                    pairProperties.reference.definition.asString(it.reference)
                )
                pairProperties.reference.capture(context, it.reference)

                pairProperties.value.writeJsonValue(
                    pairProperties.value.getPropertyAndSerialize(it, context)
                        ?: throw SerializationException("No pair value defined on $it"),
                    this,
                    context
                )
            }
            writeEndObject()
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<DO, P> {
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

                        val reference = pairProperties.reference.definition.fromString(refName, context)
                        pairProperties.reference.capture(context, reference)

                        reader.nextToken()

                        @Suppress("UNCHECKED_CAST")
                        val value = pairProperties.value.readJson(reader, context) as TO?

                        listOfTypePairs.add(
                            properties.pairModel.values {
                                mapNonNulls(
                                    this@ReferenceValuePairModel.pairProperties.reference with reference,
                                    this@ReferenceValuePairModel.pairProperties.value with value
                                )
                            }.toDataObject()
                        )
                    }
                    else -> break@walker
                }
                reader.nextToken()
            } while (token !is JsonToken.Stopped)

            return this.values(context) {
                mapNonNulls(
                    properties.referenceValuePairs withSerializable listOfTypePairs
                )
            }
        }

        override fun invoke(values: ObjectValues<DO, P>): DO =
            this@ReferenceValuePairModel.invoke(values)
    }
}
