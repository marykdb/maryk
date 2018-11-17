package maryk.core.models

import maryk.core.values.ObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** For data models which contains only reference pairs */
abstract class ReferencePairDataModel<T: Any, DO: Any, P: ReferenceValuePairsObjectPropertyDefinitions<T, DO>>(
    properties: P
) : AbstractObjectDataModel<DO, P, RequestContext, RequestContext>(properties){
    internal fun IsJsonLikeWriter.writeJsonMapObject(
        listOfPairs: List<ReferenceValuePair<*>>,
        context: RequestContext?
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

    override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<DO, P> {
        if (reader.currentToken == JsonToken.StartDocument){
            reader.nextToken()
        }

        if (reader.currentToken !is JsonToken.StartObject) {
            throw ParseException("JSON value should be an Object")
        }
        val list = mutableListOf<ReferenceValuePair<T>>()

        var currentToken = reader.nextToken()

        while (currentToken is JsonToken.FieldName) {
            @Suppress("UNCHECKED_CAST")
            val reference = ReferenceValuePair.Properties.reference.definition.fromString(
                currentToken.value ?: throw ParseException("Reference cannot be empty in filter"),
                context
            ) as IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, *, IsPropertyContext, *>, *>
            reader.nextToken()

            ReferenceValuePair.Properties.reference.capture(context, reference)

            val value = ReferenceValuePair.Properties.value.readJson(reader, context)

            @Suppress("UNCHECKED_CAST")
            list.add(ReferenceValuePair(reference, value) as ReferenceValuePair<T>)

            currentToken = reader.nextToken()
        }

        return this.map(context) {
            mapNonNulls(
                referenceValuePairs withSerializable list
            )
        }
    }
}

abstract class ReferenceValuePairsObjectPropertyDefinitions<T: Any, DO: Any> : ObjectPropertyDefinitions<DO>() {
    abstract val referenceValuePairs: ListPropertyDefinitionWrapper<ReferenceValuePair<T>, ReferenceValuePair<T>, IsPropertyContext, DO>

    protected fun <T: Any> addReferenceValuePairsDefinition(getter: (DO) -> List<ReferenceValuePair<T>>?) =
        this.add(1, "referenceValuePairs",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = {
                        @Suppress("UNCHECKED_CAST")
                        ReferenceValuePair as SimpleObjectDataModel<ReferenceValuePair<T>, ObjectPropertyDefinitions<ReferenceValuePair<T>>>
                    }
                )
            ),
            getter = getter
        )
}
