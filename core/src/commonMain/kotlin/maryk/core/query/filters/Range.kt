package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.ValueRange
import maryk.core.query.pairs.ReferenceValueRangePair
import maryk.core.values.ObjectValues
import maryk.json.IllegalJsonOperation
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/**
 * Compares [referenceRangePairs] if referred values are within given ranges.
 */
data class Range internal constructor(
    val referenceRangePairs: List<ReferenceValueRangePair<*>>
) : IsFilter {
    override val filterType = FilterType.Range

    constructor(vararg range: ReferenceValueRangePair<*>): this(range.toList())

    object Properties : ObjectPropertyDefinitions<Range>() {
        @Suppress("UNCHECKED_CAST")
        val ranges = Properties.add(1, "referenceRangePairs",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { ReferenceValueRangePair }
                ) as IsValueDefinition<ReferenceValueRangePair<*>, IsPropertyContext>
            ),
            Range::referenceRangePairs
        )
    }

    companion object: QueryDataModel<Range, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<Range, Properties>) = Range(
            referenceRangePairs = values(1)
        )

        override fun writeJson(obj: Range, writer: IsJsonLikeWriter, context: RequestContext?) {
            val ranges = obj.referenceRangePairs

            writer.writeJsonRanges(ranges, context)
        }

        private fun IsJsonLikeWriter.writeJsonRanges(
            ranges: List<ReferenceValueRangePair<*>>,
            context: RequestContext?
        ) {
            writeStartObject()
            for (it in ranges) {
                writeFieldName(
                    ReferenceValueRangePair.Properties.reference.definition.asString(it.reference)
                )
                ReferenceValueRangePair.Properties.reference.capture(context, it.reference)

                ReferenceValueRangePair.Properties.range.definition.writeJsonValue(it.range, this, context)
            }
            writeEndObject()
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<Range, Properties> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            if (reader.currentToken !is JsonToken.StartObject) {
                throw IllegalJsonOperation("Expected object at start of JSON")
            }

            val listOfRanges = mutableListOf<ReferenceValueRangePair<*>>()
            reader.nextToken()

            walker@ do {
                val token = reader.currentToken
                when (token) {
                    is JsonToken.FieldName -> {
                        val value = token.value ?: throw ParseException("Empty field name not allowed in JSON")

                        val reference = ReferenceValueRangePair.Properties.reference.definition.fromString(value, context)
                        ReferenceValueRangePair.Properties.reference.capture(context, reference)

                        reader.nextToken()

                        val range = ReferenceValueRangePair.Properties.range.readJson(reader, context)

                        @Suppress("UNCHECKED_CAST")
                        listOfRanges.add(
                            ReferenceValueRangePair(
                                reference as IsPropertyReference<Comparable<Any>, IsChangeableValueDefinition<Comparable<Any>, IsPropertyContext>, *>,
                                range as ValueRange<Comparable<Any>>
                            )
                        )
                    }
                    else -> break@walker
                }
                reader.nextToken()
            } while (token !is JsonToken.Stopped)

            return this.values(context) {
                mapNonNulls(
                    ranges withSerializable listOfRanges
                )
            }
        }
    }
}
