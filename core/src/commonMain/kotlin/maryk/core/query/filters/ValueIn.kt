package maryk.core.query.filters

import maryk.core.models.SimpleFilterDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
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

    object Properties : ObjectPropertyDefinitions<ValueIn>() {
        val referenceValuePairs = add(1, "referenceValuePairs",
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

    companion object: SimpleFilterDataModel<ValueIn, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<ValueIn, Properties>) = ValueIn(
            referenceValuePairs = map(1)
        )

        override fun writeJson(obj: ValueIn, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }

        internal fun IsJsonLikeWriter.writeJsonMapObject(
            listOfPairs: List<ReferenceValueSetPair<*>>,
            context: RequestContext?
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

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<ValueIn, Properties> {
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
                ) as IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, *, IsPropertyContext, *>, *>
                reader.nextToken()

                ReferenceValueSetPair.Properties.reference.capture(context, reference)

                val value = ReferenceValueSetPair.Properties.values.readJson(reader, context)

                list.add(ReferenceValueSetPair(reference, value))

                currentToken = reader.nextToken()
            }

            return this.map(context) {
                mapNonNulls(
                    referenceValuePairs withSerializable list
                )
            }
        }
    }
}