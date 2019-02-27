package maryk.core.processors.datastore

import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.ScanType.IndexScan
import maryk.core.processors.datastore.ScanType.TableScan
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.orders.IsOrder
import maryk.core.query.orders.Order
import maryk.core.query.orders.Orders

/** Converts an [order] to a ScanIndexType */
fun IsRootDataModel<*>.orderToScanType(order: IsOrder?): ScanType {
    return order?.let {
        when (order) {
            is Orders -> TODO("SUPPORT")
            is Order -> {
                if (order.propertyReference == null) {
                    TableScan(order.direction)
                } else {
                    val indexableMatch = this.indices?.find { indexable ->
                        when(indexable) {
                            is Multiple ->
                                indexable.references[0] == order.propertyReference
                            is Reversed<*> ->
                                indexable.reference == order.propertyReference
                            is IsIndexablePropertyReference<*> ->
                                indexable == order.propertyReference
                            else -> throw TypeException("Indexable type of $indexable is not supported")
                        }
                    } ?: throw RequestException("$order cannot be used as Order because there is no fitting index")

                    val ascending = order.direction == ASC
                    val reversed = indexableMatch is Reversed<*>

                    val direction = when {
                        (ascending && !reversed) || (!ascending && reversed) -> ASC
                        else -> DESC
                    }

                    IndexScan(indexableMatch, direction)
                }
            }
            else -> throw TypeException("Order type of $order is not supported")
        }
    } ?: TableScan(ASC)
}
