package maryk.core.query.orders

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.BaseDataModel
import maryk.core.models.QueryModel
import maryk.core.models.emptyValues
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.orders.OrderType.ORDER
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndDocument
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Suspended
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.UnknownYamlTag
import maryk.yaml.YamlWriter

/** Direction Enumeration */
enum class Direction(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<Direction>, IsCoreEnum {
    ASC(1u), DESC(2u);

    companion object : IndexedEnumDefinition<Direction>(Direction::class, { entries })
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

    companion object {
        val ascending = Order()
        val descending = Order(direction = DESC)
    }

    object Model : QueryModel<Order, Model>() {
        val propertyReference by contextual(
            index = 1u,
            getter = Order::propertyReference,
            definition = ContextualPropertyReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? BaseDataModel<*>? ?: throw ContextNotFoundException()
                }
            )
        )

        val direction by enum(
            2u,
            Order::direction,
            enum = Direction,
            default = ASC
        )

        override fun invoke(values: ObjectValues<Order, Model>): Order =
            Order(
                propertyReference = values(1u),
                direction = values(2u)
            )

        override val Serializer = object : ObjectDataModelSerializer<Order, Model, RequestContext, RequestContext>(this) {
            override fun writeObjectAsJson(
                obj: Order,
                writer: IsJsonLikeWriter,
                context: RequestContext?,
                skip: List<IsDefinitionWrapper<*, *, *, Order>>?
            ) {
                if (writer is YamlWriter) {
                    writeJsonOrderValue(
                        obj.propertyReference,
                        obj.direction,
                        writer,
                        context
                    )
                } else {
                    super.writeObjectAsJson(obj, writer, context, skip)
                }
            }

            private fun writeJsonOrderValue(
                reference: AnyPropertyReference?,
                direction: Direction,
                writer: YamlWriter,
                context: RequestContext?
            ) {
                if (direction == DESC) {
                    writer.writeTag("!Desc")
                } else if (reference == null) {
                    writer.writeTag("!Asc")
                }
                if (reference != null) {
                    propertyReference.writeJsonValue(reference, writer, context)
                }
            }

            override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<Order, Model> {
                if (reader is IsYamlReader) {
                    var currentToken = reader.currentToken

                    if (currentToken == StartDocument) {
                        currentToken = reader.nextToken()

                        when (currentToken) {
                            is Suspended -> currentToken = currentToken.lastToken
                            is EndDocument -> return emptyValues()
                            else -> Unit
                        }
                    }

                    val valueMap = MutableValueItems()

                    return when (currentToken) {
                        is StartObject -> { // when has no values
                            currentToken.type.let { valueType ->
                                if (valueType is UnknownYamlTag && valueType.name == "Desc") {
                                    valueMap += direction asValueItem DESC
                                }
                            }

                            reader.nextToken() // Read until EndObject
                            ObjectValues(this@Model, valueMap, context)
                        }
                        is Value<*> -> {
                            currentToken.type.let { valueType ->
                                if (valueType is UnknownYamlTag && valueType.name == "Desc") {
                                    valueMap += direction asValueItem DESC
                                }
                            }

                            currentToken.value.let { value ->
                                if (value is String) {
                                    valueMap += propertyReference asValueItem propertyReference.definition.fromString(
                                        value,
                                        context
                                    )
                                }
                            }

                            ObjectValues(this@Model, valueMap, context)
                        }
                        else -> throw ParseException("Expected an order definition, not $currentToken")
                    }
                } else {
                    return super.readJson(reader, context)
                }
            }
        }
    }
}
