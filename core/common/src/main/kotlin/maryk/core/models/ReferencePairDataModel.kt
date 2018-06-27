package maryk.core.models

import maryk.core.objects.ValueMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** For data models which contains only reference pairs */
internal abstract class ReferencePairDataModel<T: Any, DO: Any, P: ReferenceValuePairsPropertyDefinitions<T, DO>>(
    properties: P
) : AbstractDataModel<DO, P, DataModelPropertyContext, DataModelPropertyContext>(properties){
    override fun writeJson(map: ValueMap<DO, P>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
        map { referenceValuePairs }?.let {
            writer.writeJsonMapObject(it, context)
        }
    }

    internal fun IsJsonLikeWriter.writeJsonMapObject(
        listOfPairs: List<ReferenceValuePair<*>>,
        context: DataModelPropertyContext?
    ) {
        writeStartObject()

        for (pair in listOfPairs) {
            writeFieldName(
                ReferenceValuePair.Properties.reference.definition.asString(pair.reference, context)
            )
            ReferenceValuePair.Properties.reference.capture(context, pair.reference)
            ReferenceValuePair.Properties.value.definition.writeJsonValue(pair.value, this, context)
        }

        writeEndObject()
    }

    override fun readJson(reader: IsJsonLikeReader, context: DataModelPropertyContext?): ValueMap<DO, P> {
        if (reader.currentToken == JsonToken.StartDocument){
            reader.nextToken()
        }

        if (reader.currentToken !is JsonToken.StartObject) {
            throw ParseException("JSON value should be an Object")
        }
        val list = mutableListOf<ReferenceValuePair<*>>()

        var currentToken = reader.nextToken()

        while (currentToken is JsonToken.FieldName) {
            @Suppress("UNCHECKED_CAST")
            val reference = ReferenceValuePair.Properties.reference.definition.fromString(
                currentToken.value ?: throw ParseException("Reference cannot be empty in filter"),
                context
            ) as IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, *, IsPropertyContext, *>>
            reader.nextToken()

            ReferenceValuePair.Properties.reference.capture(context, reference)

            val value = ReferenceValuePair.Properties.value.readJson(reader, context)

            list.add(ReferenceValuePair(reference, value))

            currentToken = reader.nextToken()
        }

        return this.map {
            mapOf(
                referenceValuePairs with list
            )
        }
    }
}

internal abstract class ReferenceValuePairsPropertyDefinitions<T: Any, DO: Any> : PropertyDefinitions<DO>() {
    abstract val referenceValuePairs: ListPropertyDefinitionWrapper<ReferenceValuePair<T>, ReferenceValuePair<T>, IsPropertyContext, DO>

    protected fun <T: Any> addReferenceValuePairsDefinition(getter: (DO) -> List<ReferenceValuePair<T>>?) =
        this.add(0, "referenceValuePairs",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = {
                        @Suppress("UNCHECKED_CAST")
                        ReferenceValuePair as SimpleDataModel<ReferenceValuePair<T>, PropertyDefinitions<ReferenceValuePair<T>>>
                    }
                )
            ),
            getter = getter
        )
}
