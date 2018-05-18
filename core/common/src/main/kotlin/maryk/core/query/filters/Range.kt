package maryk.core.query.filters

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.json.TokenWithType
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.UnknownYamlTag
import maryk.yaml.YamlWriter

/** Checks if reference is within given [range] */
infix fun <T: Comparable<T>> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.inRange(
    range: ClosedRange<T>
) = Range(this, range.start, range.endInclusive, true, true)

/** Checks if reference is within range of [from] and [to] while checking if is inclusive with [inclusiveFrom] and [inclusiveTo] */
fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.inRange(
    from: T,
    to: T,
    inclusiveFrom: Boolean,
    inclusiveTo: Boolean
) = Range(this, from, to, inclusiveFrom, inclusiveTo)

/**
 * Checks if [reference] is within given range of [from] until [to] of type [T].
 * With [inclusiveFrom] and [inclusiveTo] set to true (default) it will search the range including [from] or [to]
 */
data class Range<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    val from: T,
    val to: T,
    val inclusiveFrom: Boolean = true,
    val inclusiveTo: Boolean = true
) : IsPropertyCheck<T> {
    override val filterType = FilterType.Range

    internal object Properties : PropertyDefinitions<Range<*>>() {
        val reference = IsPropertyCheck.addReference(this, Range<*>::reference)
        val from = add(1, "from", ContextualValueDefinition(
            contextualResolver = { context: DataModelPropertyContext? ->
                @Suppress("UNCHECKED_CAST")
                context?.reference?.propertyDefinition?.definition as IsValueDefinition<Any, IsPropertyContext>?
                        ?: throw ContextNotFoundException()
            }
        ), Range<*>::from)

        val to = add(2, "to", ContextualValueDefinition(
            contextualResolver = { context: DataModelPropertyContext? ->
                @Suppress("UNCHECKED_CAST")
                context?.reference?.propertyDefinition?.definition as IsValueDefinition<Any, IsPropertyContext>?
                        ?: throw ContextNotFoundException()
            }
        ), Range<*>::to)

        val inclusiveFrom = add(3, "inclusiveFrom", BooleanDefinition(default = true), Range<*>::inclusiveFrom)
        val inclusiveTo = add(4, "inclusiveTo", BooleanDefinition(default = true), Range<*>::inclusiveTo)
    }

    internal companion object: QueryDataModel<Range<*>>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = Range(
            reference = map(0),
            from = map(1),
            to = map(2),
            inclusiveFrom = map(3),
            inclusiveTo = map(4)
        )

        override fun writeJson(obj: Range<*>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonValues(
                obj.reference,
                obj.from,
                obj.to,
                obj.inclusiveFrom,
                obj.inclusiveTo,
                context
            )
        }

        override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonValues(
                map[0] as IsPropertyReference<*, *>,
                map[1] as Any,
                map[2] as Any,
                map[3] as Boolean,
                map[4] as Boolean,
                context
            )
        }

        override fun walkJsonToRead(
            reader: IsJsonLikeReader,
            valueMap: MutableMap<Int, Any>,
            context: DataModelPropertyContext?
        ) {
            (reader.currentToken as? JsonToken.FieldName)?.value?.let {
                valueMap[Properties.reference.index] =
                        Properties.reference.definition.fromString(it, context)
            } ?: throw ParseException("Expected a field name for reference on Range")

            if(reader is IsYamlReader) {
                if (reader.nextToken() !is JsonToken.StartArray) {
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

                if (reader.nextToken() !is JsonToken.EndArray) {
                    throw ParseException("Range should have two values")
                }
            } else {
                if (reader.nextToken() !is JsonToken.StartObject) {
                    throw ParseException("Range should be contained in an Object")
                }
                reader.nextToken()

                super.walkJsonToRead(reader, valueMap, context)
            }

            if(reader.nextToken() !== JsonToken.EndObject) {
                throw ParseException("Expected only 1 value on Range map")
            }
        }

        protected fun <T: Any> IsJsonLikeWriter.writeJsonValues(
            reference: IsPropertyReference<*, *>,
            from: T,
            to: T,
            inclusiveFrom: Boolean,
            inclusiveTo: Boolean,
            context: DataModelPropertyContext?
        ) {
            writeStartObject()
            writeFieldName(
                Properties.reference.definition.asString(reference, context)
            )

            Properties.reference.capture(context, reference)

            @Suppress("UNCHECKED_CAST")
            if (this is YamlWriter) {
                writeStartArray(true)

                if (!inclusiveFrom) {
                    this.writeTag("!Exclude")
                }

                Properties.from.definition.writeJsonValue(from, this, context)

                if (!inclusiveTo) {
                    this.writeTag("!Exclude")
                }

                Properties.to.definition.writeJsonValue(to, this, context)

                writeEndArray()
            } else {
                writeStartObject()

                writeJsonValue(Properties.from as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Range<*>>, this, from, context)
                writeJsonValue(Properties.inclusiveFrom as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Range<*>>, this, inclusiveFrom, context)
                writeJsonValue(Properties.to as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Range<*>>, this, to, context)
                writeJsonValue(Properties.inclusiveTo as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Range<*>>, this, inclusiveTo, context)

                writeEndObject()
            }

            writeEndObject()
        }
    }
}
