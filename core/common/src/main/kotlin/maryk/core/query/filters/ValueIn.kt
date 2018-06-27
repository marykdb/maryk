package maryk.core.query.filters

import maryk.core.models.SimpleFilterDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValueSetPair
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Referenced values in [referenceValuePairs] should be within given value set */
data class ValueIn internal constructor(
    val referenceValuePairs: List<ReferenceValueSetPair<Any>>
) : IsFilter {
    override val filterType = FilterType.ValueIn

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValueSetPair<*>): this(referenceValuePair.toList() as List<ReferenceValueSetPair<Any>>)

    internal object Properties : PropertyDefinitions<ValueIn>() {
        val referenceValuePairs = add(0, "referenceValuePairs",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = {
                        ReferenceValueSetPair
                    }
                )
            ),
            getter = ValueIn::referenceValuePairs
        )
    }

    internal companion object: SimpleFilterDataModel<ValueIn>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = ValueIn(
            referenceValuePairs = map(0)
        )

        override fun writeJson(obj: ValueIn, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }

        override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            @Suppress("UNCHECKED_CAST")
            (map[Properties.referenceValuePairs.index] as List<ReferenceValueSetPair<*>>?)?.let {
                writer.writeJsonMapObject(it, context)
            }
        }

        internal fun IsJsonLikeWriter.writeJsonMapObject(
            listOfPairs: List<ReferenceValueSetPair<*>>,
            context: DataModelPropertyContext?
        ) {
            writeStartObject()

            for (pair in listOfPairs) {
                writeFieldName(
                    ReferenceValueSetPair.Properties.reference.definition.asString(pair.reference, context)
                )
                ReferenceValueSetPair.Properties.reference.capture(context, pair.reference)
                ReferenceValueSetPair.Properties.values.definition.writeJsonValue(pair.values, this, context)
            }

            writeEndObject()
        }

        override fun readJson(reader: IsJsonLikeReader, context: DataModelPropertyContext?): Map<Int, Any> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            if (reader.currentToken !is JsonToken.StartObject) {
                throw ParseException("JSON value should be an Object")
            }
            val list = mutableListOf<ReferenceValueSetPair<*>>()

            var currentToken = reader.nextToken()

            while (currentToken is JsonToken.FieldName) {
                @Suppress("UNCHECKED_CAST")
                val reference = ReferenceValueSetPair.Properties.reference.definition.fromString(
                    currentToken.value ?: throw ParseException("Reference cannot be empty in filter"),
                    context
                ) as IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, *, IsPropertyContext, *>>
                reader.nextToken()

                ReferenceValueSetPair.Properties.reference.capture(context, reference)

                val value = ReferenceValueSetPair.Properties.values.readJson(reader, context)

                list.add(ReferenceValueSetPair(reference, value))

                currentToken = reader.nextToken()
            }

            return mapOf(
                Properties.referenceValuePairs.index to list
            )
        }
    }
}
