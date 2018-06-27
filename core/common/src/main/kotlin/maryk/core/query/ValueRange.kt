package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.objects.DataObjectMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.IsFilter
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

    internal object Properties : PropertyDefinitions<ValueRange<*>>() {
        val from = add(0, "from", ContextualValueDefinition(
            contextualResolver = { context: DataModelPropertyContext? ->
                @Suppress("UNCHECKED_CAST")
                context?.reference?.propertyDefinition?.definition as IsValueDefinition<Any, IsPropertyContext>?
                        ?: throw ContextNotFoundException()
            }
        ), ValueRange<*>::from)

        val to = add(1, "to", ContextualValueDefinition(
            contextualResolver = { context: DataModelPropertyContext? ->
                @Suppress("UNCHECKED_CAST")
                context?.reference?.propertyDefinition?.definition as IsValueDefinition<Any, IsPropertyContext>?
                        ?: throw ContextNotFoundException()
            }
        ), ValueRange<*>::to)

        val inclusiveFrom = add(2, "inclusiveFrom", BooleanDefinition(default = true), ValueRange<*>::inclusiveFrom)
        val inclusiveTo = add(3, "inclusiveTo", BooleanDefinition(default = true), ValueRange<*>::inclusiveTo)
    }

    internal companion object: QueryDataModel<ValueRange<*>>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = ValueRange(
            from = map(0),
            to = map(1),
            inclusiveFrom = map(2),
            inclusiveTo = map(3)
        )

        override fun writeJson(obj: ValueRange<*>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writeJsonValues(
                writer,
                obj.from,
                obj.to,
                obj.inclusiveFrom,
                obj.inclusiveTo,
                context
            )
        }

        override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writeJsonValues(
                writer,
                map[0] as Any,
                map[1] as Any,
                map[2] as Boolean,
                map[3] as Boolean,
                context
            )
        }

        override fun readJson(reader: IsJsonLikeReader, context: DataModelPropertyContext?): DataObjectMap<ValueRange<*>> {
            return if (reader is IsYamlReader) {
                if (reader.currentToken == JsonToken.StartDocument){
                    reader.nextToken()
                }

                val valueMap: MutableMap<Int, Any> = mutableMapOf()

                if (reader.currentToken !is JsonToken.StartArray) {
                    throw ParseException("Range should be contained in an Array")
                }

                reader.nextToken().let {
                    (it as? TokenWithType)?.type?.let {
                        if (it is UnknownYamlTag && it.name == "Exclude") {
                            valueMap[Properties.inclusiveFrom.index] = false
                        }
                    }
                }

                valueMap[Properties.from.index] = Properties.from.readJson(reader, context)

                reader.nextToken().let {
                    (it as? TokenWithType)?.type?.let {
                        if (it is UnknownYamlTag && it.name == "Exclude") {
                            valueMap[Properties.inclusiveTo.index] = false
                        }
                    }
                }

                valueMap[Properties.to.index] =
                        Properties.to.readJson(reader, context)

                if (reader.nextToken() !== JsonToken.EndArray) {
                    throw ParseException("Range should have two values")
                }

                DataObjectMap(this, valueMap)
            } else {
                super.readJson(reader, context)
            }
        }

        private fun <T: Any> writeJsonValues(
            writer: IsJsonLikeWriter,
            from: T,
            to: T,
            inclusiveFrom: Boolean,
            inclusiveTo: Boolean,
            context: DataModelPropertyContext?
        ) {
            @Suppress("UNCHECKED_CAST")
            if (writer is YamlWriter) {
                writer.writeStartArray(true)

                if (!inclusiveFrom) {
                    writer.writeTag("!Exclude")
                }

                Properties.from.definition.writeJsonValue(from, writer, context)

                if (!inclusiveTo) {
                    writer.writeTag("!Exclude")
                }

                Properties.to.definition.writeJsonValue(to, writer, context)

                writer.writeEndArray()
            } else {
                writer.writeStartObject()

                writeJsonValue(Properties.from as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>, writer, from, context)
                writeJsonValue(Properties.inclusiveFrom as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>, writer, inclusiveFrom, context)
                writeJsonValue(Properties.to as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>, writer, to, context)
                writeJsonValue(Properties.inclusiveTo as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, ValueRange<*>>, writer, inclusiveTo, context)

                writer.writeEndObject()
            }
        }
    }
}
