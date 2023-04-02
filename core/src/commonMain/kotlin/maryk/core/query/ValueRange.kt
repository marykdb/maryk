package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.QueryModel
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.values
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
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

    companion object : QueryModel<ValueRange<*>, Companion>() {
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

        override fun invoke(values: ObjectValues<ValueRange<*>, Companion>): ValueRange<*> =
            ValueRange(
                from = values<Comparable<Any>>(1u),
                to = values(2u),
                inclusiveFrom = values(3u),
                inclusiveTo = values(4u)
            )

        override val Serializer = object: ObjectDataModelSerializer<ValueRange<*>, Companion, RequestContext, RequestContext>(this) {
            override fun writeObjectAsJson(
                obj: ValueRange<*>,
                writer: IsJsonLikeWriter,
                context: RequestContext?,
                skip: List<IsDefinitionWrapper<*, *, *, ValueRange<*>>>?
            ) {
                @Suppress("UNCHECKED_CAST")
                if (writer is YamlWriter) {
                    writer.writeStartArray(true)

                    if (!obj.inclusiveFrom) {
                        writer.writeTag("!Exclude")
                    }

                    from.definition.writeJsonValue(obj.from, writer, context)

                    if (!obj.inclusiveTo) {
                        writer.writeTag("!Exclude")
                    }

                    to.definition.writeJsonValue(obj.to, writer, context)

                    writer.writeEndArray()
                } else {
                    writer.writeStartObject()

                    writeJsonValue(
                        from as IsDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>,
                        writer,
                        obj.from,
                        context
                    )
                    writeJsonValue(
                        inclusiveFrom as IsDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>,
                        writer,
                        obj.inclusiveFrom,
                        context
                    )
                    writeJsonValue(
                        to as IsDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>,
                        writer,
                        obj.to,
                        context
                    )
                    writeJsonValue(
                        inclusiveTo as IsDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>,
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
            ): ObjectValues<ValueRange<*>, Companion> {
                return if (reader is IsYamlReader) {
                    if (reader.currentToken == JsonToken.StartDocument) {
                        reader.nextToken()
                    }

                    values(context) {
                        val valueMap = MutableValueItems()

                        if (reader.currentToken !is JsonToken.StartArray) {
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

                        if (reader.nextToken() !== JsonToken.EndArray) {
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
}
