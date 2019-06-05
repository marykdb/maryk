package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.RequestContext
import maryk.core.query.orders.IsOrder
import maryk.core.query.orders.OrderType
import maryk.core.query.orders.OrderType.ORDER
import maryk.core.query.orders.OrderType.ORDERS
import maryk.core.query.orders.mapOfOrderTypeToEmbeddedObject
import maryk.core.query.responses.IsResponse
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader

/** Defines a Scan from key request. */
interface IsScanRequest<DM : IsRootDataModel<P>, P : PropertyDefinitions, RP : IsResponse> : IsFetchRequest<DM, P, RP> {
    val startKey: Key<DM>?
    val order: IsOrder?
    val limit: UInt

    companion object {
        internal fun <DO : IsScanRequest<*, *, *>, DM : IsRootDataModel<*>> addStartKey(
            definitions: ObjectPropertyDefinitions<DO>,
            getter: (DO) -> Key<DM>?
        ) =
            definitions.add(
                2u, "startKey",
                ContextualReferenceDefinition<RequestContext>(
                    required = false,
                    contextualResolver = {
                        it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                    }
                ),
                getter
            )

        internal fun <DM : Any> addOrder(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> IsOrder?) =
            definitions.add(
                8u, "order",
                OrderTypesDefinition,
                getter = getter,
                toSerializable = { value, _ ->
                    value?.let {
                        TypedValue(value.orderType, value)
                    }
                },
                fromSerializable = { value ->
                    value?.value as IsOrder
                }
            )

        internal fun <DO : Any> addLimit(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> UInt?) =
            definitions.add(
                9u, "limit",
                NumberDefinition(
                    default = 100u,
                    type = UInt32
                ),
                getter
            )
    }
}

private val multiTypeDefinition = InternalMultiTypeDefinition(
    typeEnum = OrderType,
    definitionMap = mapOfOrderTypeToEmbeddedObject
)

private object OrderTypesDefinition : IsMultiTypeDefinition<OrderType, IsOrder, RequestContext> by multiTypeDefinition {
    override fun writeJsonValue(value: TypedValue<OrderType, IsOrder>, writer: IsJsonLikeWriter, context: RequestContext?) {
        @Suppress("UNCHECKED_CAST")
        val definition = mapOfOrderTypeToEmbeddedObject[value.type] as IsEmbeddedObjectDefinition<IsOrder, *, *, RequestContext, RequestContext>?
            ?: throw DefNotFoundException("No def found for index ${value.type.name}")

        definition.writeJsonValue(value.value, writer, context)
    }

    override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): TypedValue<OrderType, IsOrder> {
        val value: IsOrder = when {
            reader.currentToken is StartArray -> {
                mapOfOrderTypeToEmbeddedObject.getValue(ORDERS).readJson(reader, context)
            }
            (reader.currentToken is Value<*> && reader is IsYamlReader) ||
                    (reader.currentToken is StartObject && reader !is IsYamlReader) -> {
                mapOfOrderTypeToEmbeddedObject.getValue(ORDER).readJson(reader, context)
            }
            else -> throw ParseException("Expected an array with orders or an order")
        }

        return TypedValue(value.orderType, value)
    }
}
