package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.RequestContext
import maryk.core.query.orders.Order
import maryk.core.query.responses.IsResponse

/** Defines a Scan from key request. */
interface IsScanRequest<DM : IsRootDataModel<P>, P : PropertyDefinitions, RP : IsResponse> : IsFetchRequest<DM, P, RP> {
    val startKey: Key<DM>?
    val order: Order?
    val limit: UInt

    companion object {
        internal fun <DO : IsScanRequest<*, *, *>, DM : IsRootDataModel<*>> addStartKey(
            definitions: ObjectPropertyDefinitions<DO>,
            getter: (DO) -> Key<DM>?
        ) =
            definitions.add(
                2, "startKey",
                ContextualReferenceDefinition<RequestContext>(
                    required = false,
                    contextualResolver = {
                        it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                    }
                ),
                getter
            )

        internal fun <DM : Any> addOrder(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> Order?) =
            definitions.add(7, "order",
                EmbeddedObjectDefinition(
                    required = false,
                    dataModel = { Order }
                ),
                getter
            )

        internal fun <DO : Any> addLimit(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> UInt?) =
            definitions.add(
                8, "limit",
                NumberDefinition(
                    default = 100u,
                    type = UInt32
                ),
                getter
            )
    }
}
