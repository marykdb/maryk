package maryk.core.query.orders

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.orders.OrderType.ORDER
import maryk.core.values.EmptyValueItems
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.json.JsonToken.EndDocument
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.UnknownYamlTag
import maryk.yaml.YamlWriter

/** Direction Enumeration */
enum class Direction(override val index: UInt) : IndexedEnum<Direction> {
    ASC(1u), DESC(2u);

    companion object : IndexedEnumDefinition<Direction>("Direction", Direction::values)
}

/** Descending ordering of property */
fun AnyPropertyReference.descending() = Order(this, DESC)

/** Ascending ordering of property */
fun AnyPropertyReference.ascending() = Order(this, ASC)

/**
 * To define the order of results of property referred to [propertyReference] into [direction]
 */
data class Order internal constructor(
    val propertyReference: AnyPropertyReference? = null,
    val direction: Direction = ASC
) : IsOrder {

    override val orderType = ORDER

    object Properties : ObjectPropertyDefinitions<Order>() {
        val propertyReference = add(
            1, "propertyReference",
            ContextualPropertyReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel?.properties as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException()
                }
            ),
            Order::propertyReference
        )

        val direction = add(
            2, "direction",
            EnumDefinition(
                enum = Direction,
                default = ASC
            ),
            Order::direction
        )
    }

    companion object : QueryDataModel<Order, Properties>(
        properties = Properties
    ) {
        val ascending = Order()
        val descending = Order(direction = DESC)

        override fun invoke(values: ObjectValues<Order, Properties>) =
            Order(
                propertyReference = values(1),
                direction = values(2)
            )

        override fun writeJson(obj: Order, writer: IsJsonLikeWriter, context: RequestContext?) {
            if (writer is YamlWriter) {
                writeJsonOrderValue(
                    obj.propertyReference,
                    obj.direction,
                    writer,
                    context
                )
            } else {
                super.writeJson(obj, writer, context)
            }
        }

        private fun writeJsonOrderValue(
            reference: AnyPropertyReference?,
            direction: Direction,
            writer: YamlWriter,
            context: RequestContext?
        ) {
            if (direction == Direction.DESC) {
                writer.writeTag("!Desc")
            } else if (reference == null) {
                writer.writeTag("!Asc")
            }
            if (reference != null) {
                Properties.propertyReference.writeJsonValue(reference, writer, context)
            }
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<Order, Properties> {
            if (reader is IsYamlReader) {
                var currentToken = reader.currentToken

                if (currentToken == JsonToken.StartDocument) {
                    currentToken = reader.nextToken()

                    when (currentToken) {
                        is JsonToken.Suspended -> currentToken = currentToken.lastToken
                        is EndDocument -> return this.values(context) { EmptyValueItems }
                        else -> Unit
                    }
                }

                val valueMap = MutableValueItems()

                return when (currentToken) {
                    is JsonToken.StartObject -> { // when has no values
                        currentToken.type.let { valueType ->
                            if (valueType is UnknownYamlTag && valueType.name == "Desc") {
                                valueMap += Properties.direction withNotNull Direction.DESC
                            }
                        }

                        reader.nextToken() // Read until EndObject
                        this.values(context) { valueMap }
                    }
                    is JsonToken.Value<*> -> {
                        this.values(context) {
                            currentToken.type.let { valueType ->
                                if (valueType is UnknownYamlTag && valueType.name == "Desc") {
                                    valueMap += direction withNotNull Direction.DESC
                                }
                            }

                            currentToken.value.let { value ->
                                if (value is String) {
                                    valueMap += propertyReference withNotNull propertyReference.definition.fromString(
                                        value,
                                        context
                                    )
                                }
                            }

                            valueMap
                        }
                    }
                    else -> throw ParseException("Expected an order definition, not $currentToken")
                }
            } else {
                return super.readJson(reader, context)
            }
        }
    }
}
