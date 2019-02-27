package maryk.core.processors.datastore

import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.ScanType.IndexScan
import maryk.core.processors.datastore.ScanType.TableScan
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.orders.IsOrder
import maryk.core.query.orders.Order
import maryk.core.query.orders.Orders

/** Converts an [order] to a ScanIndexType */
fun IsRootDataModel<*>.orderToScanType(order: IsOrder?): ScanType {
    return when (order) {
        null -> TableScan(ASC)
        is Order -> singleOrderToScanType(order)
        is Orders -> {
            // Skip out all simple cases or crash on not allowed values
            when (order.orders.size) {
                0 -> return TableScan()
                1 -> return this.singleOrderToScanType(order.orders[0])
                2 -> order.orders[1].let {
                    if (it.propertyReference == null) {
                        return if (order.orders[1].direction == ASC) {
                            this.singleOrderToScanType(order.orders[0]).apply {
                                if (direction == DESC) throw RequestException("Cannot have a reversed TableScan as last index parameter")
                            }
                        } else {
                            this.singleOrderToScanType(order.orders[0]).apply {
                                if (direction == ASC) throw RequestException("Cannot have a reversed TableScan as last index parameter")
                            }
                        }
                    } // else continue
                } // else continue
            }

            // Walk all indices and try to match given Orders
            this.indices?.let {
                indexLoop@ for (indexable in it) {
                    var direction: Direction? = null

                    if (indexable !is Multiple) {
                        continue@indexLoop // Non multiple indices should be catched with simple cases above
                    } else if (order.orders.size > indexable.references.size + 1) {
                        continue@indexLoop // If more orders than index size then skip
                    } else if (order.orders.size > indexable.references.size && order.orders.last().propertyReference != null) {
                        continue@indexLoop // If one more order than index size is not default order, then skip
                    } else {
                        // Walk all sub indexables inside Multiple
                        subIndexLoop@ for ((index, subIndexable) in indexable.references.withIndex()) {
                            val currentOrderPart = order.orders.getOrNull(index)
                                ?: return IndexScan(indexable, direction!!) // direction is never null since 0 sized orders are skipped out early


                            // When order is native Table order
                            if (currentOrderPart.propertyReference == null) {
                                when {
                                    index != indexable.references.lastIndex ->
                                        throw RequestException("An Order on Table is only allowed to be the last or only one")
                                    order.orders[1].direction == direction ->
                                        return IndexScan(indexable, direction)
                                    else -> continue@indexLoop
                                }
                            }

                            val refToMatch = when (subIndexable) {
                                is Reversed<*> -> {
                                    // Only continue if order is correct
                                    when (direction) {
                                        null -> direction = when (currentOrderPart.direction) {
                                            ASC -> DESC
                                            DESC -> ASC
                                        }
                                        ASC -> if (currentOrderPart.direction == ASC) continue@indexLoop
                                        DESC -> if (currentOrderPart.direction == DESC) continue@indexLoop
                                    }
                                    subIndexable.reference
                                }
                                else -> {
                                    // Only continue if order is correct
                                    when (direction) {
                                        null -> direction = when (currentOrderPart.direction) {
                                            ASC -> ASC
                                            DESC -> DESC
                                        }
                                        ASC -> if (currentOrderPart.direction == DESC) continue@indexLoop
                                        DESC -> if (currentOrderPart.direction == ASC) continue@indexLoop
                                    }
                                    subIndexable
                                }
                            }

                            // Continue to next indexable if not matches
                            if (refToMatch != currentOrderPart.propertyReference) continue@indexLoop
                        }

                        // If no direction was set it was an empty Multiple index so skip
                        if (direction == null) continue@indexLoop

                        // Catch check the last table order of indexable found before
                        if (order.orders.size > indexable.references.size) {
                            val last = order.orders.last()
                            if (last.propertyReference == null) {
                                when (direction) {
                                    // Index match fount
                                    last.direction -> return IndexScan(indexable, direction)
                                    else -> throw RequestException("Cannot have a reversed Table order as last index parameter compared to index scan direction")
                                }
                            } else continue@indexLoop
                        } else {
                            // Index match found
                            return IndexScan(indexable, direction)
                        }
                    }
                }

                throw RequestException("No index match found on model ${this.name} for order $order")
            } ?: throw RequestException("No indices defined on model ${this.name} so order $order is not allowed")
        }
        else -> throw TypeException("Order type of $order is not supported")
    }
}

/** Convert Single [order] to ScanType */
private fun IsRootDataModel<*>.singleOrderToScanType(order: Order): ScanType {
    return if (order.propertyReference == null) {
        TableScan(order.direction)
    } else {
        var reversed = false
        val ascending = order.direction == ASC

        val indexableMatch = this.indices?.find { indexable ->
            when (indexable) {
                is Multiple ->
                    when(val subIndexable = indexable.references[0]) {
                        is Reversed<*> -> {
                            reversed = true
                            subIndexable.reference == order.propertyReference
                        }
                        else -> subIndexable == order.propertyReference
                    }
                is Reversed<*> ->{
                    reversed = true
                    indexable.reference == order.propertyReference
                }
                is IsIndexablePropertyReference<*> ->
                    indexable == order.propertyReference
                else -> throw TypeException("Indexable type of $indexable is not supported")
            }
        } ?: throw RequestException("$order cannot be used as Order because there is no fitting index")

        val direction = when {
            (ascending && !reversed) || (!ascending && reversed) -> ASC
            else -> DESC
        }

        IndexScan(indexableMatch, direction)
    }
}
