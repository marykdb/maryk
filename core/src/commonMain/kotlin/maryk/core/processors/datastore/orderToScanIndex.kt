package maryk.core.processors.datastore

import maryk.core.processors.datastore.ScanType.IndexScan
import maryk.core.processors.datastore.ScanType.StoreScan
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.IsOrder
import maryk.core.query.orders.Order
import maryk.core.query.orders.Orders

/** Converts an [order] to a ScanIndexType */
fun orderToScanType(order: IsOrder?): ScanType {
    return order?.let {
        when (order) {
            is Orders -> TODO("SUPPORT")
            is Order -> {
                if (order.propertyReference == null) {
                    StoreScan(order.direction)
                } else {
                    IndexScan
                }
            }
            else -> TODO("SUPPORT")
        }
    } ?: StoreScan(ASC)
}
