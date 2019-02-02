package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.UnknownYamlTag
import maryk.yaml.YamlWriter

/** Direction Enumeration */
enum class Direction(override val index: UInt) : IndexedEnum<Direction> {
    ASC(1u), DESC(2u);

    companion object: IndexedEnumDefinition<Direction>("Direction", Direction::values)
}

/** Descending ordering of property */
fun AnyPropertyReference.descending() = Order(this, Direction.DESC)

/** Ascending ordering of property */
fun AnyPropertyReference.ascending() = Order(this, Direction.ASC)

/**
 * To define the order of results of property referred to [propertyReference] into [direction]
 */
data class Order internal constructor(
    val propertyReference: AnyPropertyReference,
    val direction: Direction = Direction.ASC
) {
    object Properties : ObjectPropertyDefinitions<Order>() {
        val propertyReference = add(1, "propertyReference", ContextualPropertyReferenceDefinition<RequestContext>(
            contextualResolver = {
                it?.dataModel?.properties as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException()
            }
        ), Order::propertyReference)

        val direction = add(2, "direction", EnumDefinition(
            enum = Direction,
            default = Direction.ASC
        ), Order::direction)
    }

    companion object: QueryDataModel<Order, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<Order, Properties>) = Order(
            propertyReference = values(1),
            direction = values(2)
        )

        override fun writeJson(obj: Order, writer: IsJsonLikeWriter, context: RequestContext?) {
            if (writer is YamlWriter) {
                writeJsonOrderValue(obj.propertyReference, obj.direction, writer, context)
            } else {
                super.writeJson(obj, writer, context)
            }
        }

        private fun writeJsonOrderValue(
            reference: AnyPropertyReference,
            direction: Direction,
            writer: YamlWriter,
            context: RequestContext?
        ) {
            if (direction == Direction.DESC) {
                writer.writeTag("!Desc")
            }
            Properties.propertyReference.writeJsonValue(reference, writer, context)
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<Order, Properties> {
            if (reader is IsYamlReader) {
                var currentToken = reader.currentToken

                if (currentToken == JsonToken.StartDocument) {
                    currentToken = reader.nextToken()

                    if (currentToken is JsonToken.Suspended) {
                        currentToken = currentToken.lastToken
                    }
                }

                @Suppress("UNCHECKED_CAST")
                (currentToken as? JsonToken.Value<String>)?.let {
                    return this.values(context) {
                        val valueMap = MutableValueItems()

                        it.type.let { valueType ->
                            if (valueType is UnknownYamlTag && valueType.name == "Desc") {
                                valueMap += direction withNotNull Direction.DESC
                            }
                        }

                        valueMap += propertyReference withNotNull propertyReference.definition.fromString(
                            it.value,
                            context
                        )

                        valueMap
                    }
                } ?: throw ParseException("Expected only a property reference in Order")
            } else {
                return super.readJson(reader, context)
            }
        }
    }
}
