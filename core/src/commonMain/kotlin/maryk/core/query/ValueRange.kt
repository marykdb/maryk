package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.IsFilter
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
data class ValueRange<T: Any> internal constructor(
    val from: T,
    val to: T,
    val inclusiveFrom: Boolean = true,
    val inclusiveTo: Boolean = true
) : IsFilter {
    override val filterType = FilterType.Range

    object Properties : ObjectPropertyDefinitions<ValueRange<*>>() {
        val from = add(1, "from", ContextualValueDefinition(
            contextualResolver = { context: RequestContext? ->
                @Suppress("UNCHECKED_CAST")
                context?.reference?.propertyDefinition?.definition as? IsValueDefinition<Any, IsPropertyContext>?
                        ?: throw ContextNotFoundException()
            }
        ), ValueRange<*>::from)

        val to = add(2, "to", ContextualValueDefinition(
            contextualResolver = { context: RequestContext? ->
                @Suppress("UNCHECKED_CAST")
                context?.reference?.propertyDefinition?.definition as? IsValueDefinition<Any, IsPropertyContext>?
                        ?: throw ContextNotFoundException()
            }
        ), ValueRange<*>::to)

        val inclusiveFrom = add(3, "inclusiveFrom", BooleanDefinition(default = true), ValueRange<*>::inclusiveFrom)
        val inclusiveTo = add(4, "inclusiveTo", BooleanDefinition(default = true), ValueRange<*>::inclusiveTo)
    }

    companion object: QueryDataModel<ValueRange<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<ValueRange<*>, Properties>) = ValueRange(
            from = map(1),
            to = map(2),
            inclusiveFrom = map(3),
            inclusiveTo = map(4)
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

                writeJsonValue(Properties.from as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>, writer, obj.from, context)
                writeJsonValue(Properties.inclusiveFrom as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>, writer, obj.inclusiveFrom, context)
                writeJsonValue(Properties.to as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>, writer, obj.to, context)
                writeJsonValue(Properties.inclusiveTo as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>, writer, obj.inclusiveTo, context)

                writer.writeEndObject()
            }
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<ValueRange<*>, Properties> {
            return if (reader is IsYamlReader) {
                if (reader.currentToken == JsonToken.StartDocument){
                    reader.nextToken()
                }

                this.map(context) {
                    val valueMap = MutableValueItems()

                    if (reader.currentToken !is JsonToken.StartArray) {
                        throw ParseException("Range should be contained in an Array")
                    }

                    reader.nextToken().let {
                        (it as? TokenWithType)?.type?.let { tokenType ->
                            if (tokenType is UnknownYamlTag && tokenType.name == "Exclude") {
                                valueMap[Properties.inclusiveFrom.index] = false
                            }
                        }
                    }

                    valueMap += from withNotNull from.readJson(reader, context)

                    reader.nextToken().let {
                        (it as? TokenWithType)?.type?.let { tokenType ->
                            if (tokenType is UnknownYamlTag && tokenType.name == "Exclude") {
                                valueMap[Properties.inclusiveTo.index] = false
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
