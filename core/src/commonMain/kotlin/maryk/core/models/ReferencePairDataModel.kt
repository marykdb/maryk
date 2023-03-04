package maryk.core.models

import maryk.core.exceptions.SerializationException
import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.query.changes.MultiTypeChange.Properties
import maryk.core.query.pairs.ReferenceValuePairPropertyDefinitions
import maryk.core.values.ObjectValues
import maryk.json.IllegalJsonOperation
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Stopped
import maryk.lib.exceptions.ParseException

/** For data models which contains only reference pairs of type [R] */
abstract class ReferencePairDataModel<DO : Any, P : ReferenceValuePairsObjectPropertyDefinitions<DO, R>, R : DefinedByReference<*>, T : Any, TO: Any>(
    properties: P,
    private val pairProperties: ReferenceValuePairPropertyDefinitions<R, T, TO, IsDefinitionWrapper<T, TO, RequestContext, R>>
) : QueryDataModel<DO, P>(properties) {
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
        if (reader.currentToken == StartDocument) {
            reader.nextToken()
        }

        if (reader.currentToken !is StartObject) {
            throw IllegalJsonOperation("Expected object at start of JSON")
        }

        val listOfTypePairs = mutableListOf<R>()
        reader.nextToken()

        walker@ do {
            val token = reader.currentToken
            when (token) {
                is FieldName -> {
                    val refName = token.value ?: throw ParseException("Empty field name not allowed in JSON")

                    val reference = pairProperties.reference.definition.fromString(refName, context)
                    pairProperties.reference.capture(context, reference)

                    reader.nextToken()

                    @Suppress("UNCHECKED_CAST")
                    val value = pairProperties.value.readJson(reader, context) as TO?

                    listOfTypePairs.add(
                        properties.pairModel.values {
                            mapNonNulls(
                                pairProperties.reference with reference,
                                pairProperties.value with value
                            )
                        }.toDataObject()
                    )
                }
                else -> break@walker
            }
            reader.nextToken()
        } while (token !is Stopped)

        return this.values(context) {
            Properties.mapNonNulls(
                properties.referenceValuePairs withSerializable listOfTypePairs
            )
        }
    }
}

/** Defines the PropertyDefinitions of DataModels with only Reference Value pairs */
abstract class ReferenceValuePairsObjectPropertyDefinitions<DO : Any, R : DefinedByReference<*>>(
    val pairName: String,
    pairGetter: (DO) -> List<R>?,
    val pairModel: QueryDataModel<R, out IsObjectPropertyDefinitions<R>>
) : ObjectPropertyDefinitions<DO>() {
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
}
