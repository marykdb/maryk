package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.UnknownYamlTag
import maryk.yaml.YamlWriter

/** Direction Enumeration */
enum class Direction(override val index: Int) : IndexedEnum<Direction> {
    ASC(0), DESC(1);

    companion object: IndexedEnumDefinition<Direction>("Direction", Direction::values)
}

/** Descending ordering of property */
fun IsPropertyReference<*, *>.descending() = Order(this, Direction.DESC)

/** Ascending ordering of property */
fun IsPropertyReference<*, *>.ascending() = Order(this, Direction.ASC)

/**
 * To define the order of results of property referred to [propertyReference] into [direction]
 */
data class Order internal constructor(
    val propertyReference: IsPropertyReference<*, *>,
    val direction: Direction = Direction.ASC
) {
    internal object Properties : ObjectPropertyDefinitions<Order>() {
        val propertyReference = add(0, "propertyReference", ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
            contextualResolver = {
                it?.dataModel?.properties as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException()
            }
        ), Order::propertyReference)

        val direction = add(1, "direction", EnumDefinition(
            enum = Direction,
            default = Direction.ASC
        ), Order::direction)
    }

    internal companion object: QueryDataModel<Order, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<Order, Properties>) = Order(
            propertyReference = map(0),
            direction = map(1)
        )

        override fun writeJson(obj: Order, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            if (writer is YamlWriter) {
                writeJsonOrderValue(obj.propertyReference, obj.direction, writer, context)
            } else {
                super.writeJson(obj, writer, context)
            }
        }

        private fun writeJsonOrderValue(
            reference: IsPropertyReference<*, *>,
            direction: Direction,
            writer: YamlWriter,
            context: DataModelPropertyContext?
        ) {
            if (direction == Direction.DESC) {
                writer.writeTag("!Desc")
            }
            Properties.propertyReference.writeJsonValue(reference, writer, context)
        }

        override fun readJson(reader: IsJsonLikeReader, context: DataModelPropertyContext?): ObjectValues<Order, Properties> {
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
                    return this.map {
                        val valueMap = mutableMapOf<Int, Any?>()

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
