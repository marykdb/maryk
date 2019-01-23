@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.query.changes

import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.enum.AnyIndexedEnum
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceTypePair
import maryk.core.values.ObjectValues
import maryk.json.IllegalJsonOperation
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Defines a change in type for complex multitype value */
data class MultiTypeChange internal constructor(
    val referenceTypePairs: List<ReferenceTypePair<*>>
) : IsChange {
    override val changeType = ChangeType.TypeChange

    constructor(vararg referenceTypePair: ReferenceTypePair<*>): this(referenceTypePair.toList())

    object Properties : ObjectPropertyDefinitions<MultiTypeChange>() {
        @Suppress("UNCHECKED_CAST")
        val referenceTypePairs = add(1, "referenceTypePairs",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { ReferenceTypePair }
                ) as IsValueDefinition<ReferenceTypePair<*>, RequestContext>
            ),
            MultiTypeChange::referenceTypePairs
        )
    }

    companion object: QueryDataModel<MultiTypeChange, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<MultiTypeChange, Properties>) = MultiTypeChange(
            referenceTypePairs = values(1)
        )

        override fun writeJson(
            values: ObjectValues<MultiTypeChange, Properties>,
            writer: IsJsonLikeWriter,
            context: RequestContext?
        ) {
            val referenceTypePairs = values { referenceTypePairs } ?: throw ParseException("ranges was not set on Range")
            writer.writeJsonTypePairs(referenceTypePairs, context)
        }

        override fun writeJson(obj: MultiTypeChange, writer: IsJsonLikeWriter, context: RequestContext?) {
            val ranges = obj.referenceTypePairs

            writer.writeJsonTypePairs(ranges, context)
        }

        private fun IsJsonLikeWriter.writeJsonTypePairs(
            referenceTypePairs: List<ReferenceTypePair<*>>,
            context: RequestContext?
        ) {
            writeStartObject()
            for (it in referenceTypePairs) {
                writeFieldName(
                    ReferenceTypePair.Properties.reference.definition.asString(it.reference)
                )
                ReferenceTypePair.Properties.reference.capture(context, it.reference)

                @Suppress("UNCHECKED_CAST")
                ReferenceTypePair.Properties.type.definition.writeJsonValue(it.type as AnyIndexedEnum, this, context)
            }
            writeEndObject()
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<MultiTypeChange, Properties> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            if (reader.currentToken !is JsonToken.StartObject) {
                throw IllegalJsonOperation("Expected object at start of JSON")
            }

            val listOfTypePairs = mutableListOf<ReferenceTypePair<*>>()
            reader.nextToken()

            walker@ do {
                val token = reader.currentToken
                when (token) {
                    is JsonToken.FieldName -> {
                        val value = token.value ?: throw ParseException("Empty field name not allowed in JSON")

                        val reference = ReferenceTypePair.Properties.reference.definition.fromString(value, context)
                        ReferenceTypePair.Properties.reference.capture(context, reference)

                        reader.nextToken()

                        val type = ReferenceTypePair.Properties.type.readJson(reader, context)

                        @Suppress("UNCHECKED_CAST")
                        listOfTypePairs.add(
                            ReferenceTypePair(
                                reference as MultiTypePropertyReference<AnyIndexedEnum, *, MultiTypeDefinitionWrapper<AnyIndexedEnum, *, *, *>, *>,
                                type
                            )
                        )
                    }
                    else -> break@walker
                }
                reader.nextToken()
            } while (token !is JsonToken.Stopped)

            return this.values(context) {
                mapNonNulls(
                    referenceTypePairs withSerializable listOfTypePairs
                )
            }
        }
    }
}
