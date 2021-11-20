package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartDocument
import maryk.json.TokenWithType
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.UnknownYamlTag
import maryk.yaml.YamlWriter

/**
 * Defines a range of [from] until [to] of type [T].
 * With [inclusiveFrom] and [inclusiveTo] set to true (default) it will include [from] or [to]
 */
data class ValueRange<T : Comparable<T>> internal constructor(
    val from: T,
    val to: T,
    val inclusiveFrom: Boolean = true,
    val inclusiveTo: Boolean = true
) {
    /** Check if value is contained in range */
    operator fun contains(value: T): Boolean = when {
        value < from -> false
        value > to -> false
        value == from && !inclusiveFrom -> false
        value == to && !inclusiveTo -> false
        else -> true
    }

    object Properties : ObjectPropertyDefinitions<ValueRange<*>>() {
        val from by contextual(
            index = 1u,
            getter = ValueRange<*>::from,
            definition = ContextualValueDefinition(
                contextualResolver = { context: RequestContext? ->
                    @Suppress("UNCHECKED_CAST")
                    context?.reference?.comparablePropertyDefinition as? IsValueDefinition<Any, IsPropertyContext>?
                        ?: throw ContextNotFoundException()
                }
            )
        )

        val to by contextual(
            index = 2u,
            getter = ValueRange<*>::to,
            definition = ContextualValueDefinition(
                contextualResolver = { context: RequestContext? ->
                    @Suppress("UNCHECKED_CAST")
                    context?.reference?.comparablePropertyDefinition as? IsValueDefinition<Any, IsPropertyContext>?
                        ?: throw ContextNotFoundException()
                }
            )
        )

        val inclusiveFrom by boolean(3u, default = true, getter = ValueRange<*>::inclusiveFrom)
        val inclusiveTo by boolean(4u, default = true, getter = ValueRange<*>::inclusiveTo)
    }

    companion object : QueryDataModel<ValueRange<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ValueRange<*>, Properties>) = ValueRange(
            from = values<Comparable<Any>>(1u),
            to = values(2u),
            inclusiveFrom = values(3u),
            inclusiveTo = values(4u)
        )

        override fun writeJson(obj: ValueRange<*>, writer: IsJsonLikeWriter, context: RequestContext?) {
            @Suppress("UNCHECKED_CAST")
            if (writer is YamlWriter) {
                writer.writeStartArray(true)

                if (!obj.inclusiveFrom) {
                    writer.writeTag("!Exclude")
                }

                Properties.from.definition.writeJsonValue(obj.from, writer, context)

                if (!obj.inclusiveTo) {
                    writer.writeTag("!Exclude")
                }

                Properties.to.definition.writeJsonValue(obj.to, writer, context)

                writer.writeEndArray()
            } else {
                writer.writeStartObject()

                writeJsonValue(
                    Properties.from as IsDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>,
                    writer,
                    obj.from,
                    context
                )
                writeJsonValue(
                    Properties.inclusiveFrom as IsDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>,
                    writer,
                    obj.inclusiveFrom,
                    context
                )
                writeJsonValue(
                    Properties.to as IsDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>,
                    writer,
                    obj.to,
                    context
                )
                writeJsonValue(
                    Properties.inclusiveTo as IsDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>,
                    writer,
                    obj.inclusiveTo,
                    context
                )

                writer.writeEndObject()
            }
        }

        override fun readJson(
            reader: IsJsonLikeReader,
            context: RequestContext?
        ): ObjectValues<ValueRange<*>, Properties> {
            return if (reader is IsYamlReader) {
                if (reader.currentToken == StartDocument) {
                    reader.nextToken()
                }

                this.values(context) {
                    val valueMap = MutableValueItems()

                    if (reader.currentToken !is StartArray) {
                        throw ParseException("Range should be contained in an Array")
                    }

                    reader.nextToken().let {
                        (it as? TokenWithType)?.type?.let { tokenType ->
                            if (tokenType is UnknownYamlTag && tokenType.name == "Exclude") {
                                valueMap[inclusiveFrom.index] = false
                            }
                        }
                    }

                    valueMap += from withNotNull from.readJson(reader, context)

                    reader.nextToken().let {
                        (it as? TokenWithType)?.type?.let { tokenType ->
                            if (tokenType is UnknownYamlTag && tokenType.name == "Exclude") {
                                valueMap[inclusiveTo.index] = false
                            }
                        }
                    }

                    valueMap += to withNotNull to.readJson(reader, context)

                    if (reader.nextToken() !== EndArray) {
                        throw ParseException("Range should have two values")
                    }
                    valueMap
                }
            } else {
                super.readJson(reader, context)
            }
        }
    }
}
