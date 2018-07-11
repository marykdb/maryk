package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.objects.Values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.ValueRange
import maryk.core.query.pairs.ReferenceValueRangePair
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
) {
    constructor(vararg range: ReferenceValueRangePair<*>): this(range.toList())

    internal object Properties : ObjectPropertyDefinitions<Range>() {
        val ranges = Properties.add(0, "referenceRangePairs",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { ReferenceValueRangePair }
                )
            ),
            Range::referenceRangePairs
        )
    }

    internal companion object: QueryDataModel<Range, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: Values<Range, Properties>) = Range(
            referenceRangePairs = map(0)
        )

        override fun writeJson(map: Values<Range, Properties>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            val ranges = map { ranges } ?: throw ParseException("Ranges missing in Range")

            writer.writeJsonRanges(ranges, context)
        }

        override fun writeJson(obj: Range, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            val ranges = obj.referenceRangePairs

            writer.writeJsonRanges(ranges, context)
        }

        private fun IsJsonLikeWriter.writeJsonRanges(
            ranges: List<ReferenceValueRangePair<*>>,
            context: DataModelPropertyContext?
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

        override fun readJson(reader: IsJsonLikeReader, context: DataModelPropertyContext?): Values<Range, Properties> {
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
                                reference as IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, *, IsPropertyContext, *>>,
                                range as ValueRange<Any>
                            )
                        )
                    }
                    else -> break@walker
                }
                reader.nextToken()
            } while (token !is JsonToken.Stopped)

            return this.map {
                mapOf(
                    ranges with listOfRanges
                )
            }
        }
    }
}
