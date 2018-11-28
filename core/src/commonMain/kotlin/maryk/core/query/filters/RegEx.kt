package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceValueRegexPair
import maryk.core.values.ObjectValues
import maryk.json.IllegalJsonOperation
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Referenced values in [referenceValuePairs] should match with regular expressions */
data class RegEx internal constructor(
    val referenceValuePairs: List<ReferenceValueRegexPair>
) : IsFilter {
    override val filterType = FilterType.RegEx

    constructor(vararg referenceValuePair: ReferenceValueRegexPair): this(referenceValuePair.toList())

    object Properties : ObjectPropertyDefinitions<RegEx>() {
        val referenceValuePairs = this.add(1, "referenceValuePairs",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = {
                        @Suppress("UNCHECKED_CAST")
                        ReferenceValueRegexPair as SimpleObjectDataModel<ReferenceValueRegexPair, ObjectPropertyDefinitions<ReferenceValueRegexPair>>
                    }
                )
            ),
            getter = RegEx::referenceValuePairs
        )
    }

    companion object: QueryDataModel<RegEx, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<RegEx, Properties>) = RegEx(
            referenceValuePairs = values(1)
        )

        override fun writeJson(obj: RegEx, writer: IsJsonLikeWriter, context: RequestContext?) {
            val ranges = obj.referenceValuePairs
            writer.writeJsonRegexes(ranges, context)
        }

        private fun IsJsonLikeWriter.writeJsonRegexes(
            ranges: List<ReferenceValueRegexPair>,
            context: RequestContext?
        ) {
            writeStartObject()
            for (it in ranges) {
                writeFieldName(
                    ReferenceValueRegexPair.Properties.reference.definition.asString(it.reference)
                )
                ReferenceValueRegexPair.Properties.reference.capture(context, it.reference)
                ReferenceValueRegexPair.Properties.regex.definition.writeJsonValue(it.regex.pattern, this, context)
            }
            writeEndObject()
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<RegEx, Properties> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            if (reader.currentToken !is JsonToken.StartObject) {
                throw IllegalJsonOperation("Expected object at start of JSON")
            }

            val listOfRegexes = mutableListOf<ReferenceValueRegexPair>()
            reader.nextToken()

            walker@ do {
                val token = reader.currentToken
                when (token) {
                    is JsonToken.FieldName -> {
                        val value = token.value ?: throw ParseException("Empty field name not allowed in JSON")

                        val reference = ReferenceValueRegexPair.Properties.reference.definition.fromString(value, context)
                        ReferenceValueRegexPair.Properties.reference.capture(context, reference)

                        reader.nextToken()

                        val regex = ReferenceValueRegexPair.Properties.regex.readJson(reader, context)

                        @Suppress("UNCHECKED_CAST")
                        listOfRegexes.add(
                            ReferenceValueRegexPair(
                                reference as IsPropertyReference<String, IsValuePropertyDefinitionWrapper<String, *, IsPropertyContext, *>, *>,
                                Regex(regex)
                            )
                        )
                    }
                    else -> break@walker
                }
                reader.nextToken()
            } while (token !is JsonToken.Stopped)

            return this.values(context) {
                mapNonNulls(
                    referenceValuePairs withSerializable listOfRegexes
                )
            }
        }
    }
}
