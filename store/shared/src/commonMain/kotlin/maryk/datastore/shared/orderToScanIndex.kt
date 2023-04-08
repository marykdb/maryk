package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.orders.IsOrder
import maryk.core.query.orders.Order
import maryk.core.query.orders.Orders
import maryk.core.query.pairs.ReferenceValuePair
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan

/** Converts an [order] to a ScanIndexType */
fun IsRootDataModel.orderToScanType(
    order: IsOrder?,
    equalPairs: List<ReferenceValuePair<*>>
): ScanType {
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

            // Check if is default Table scan
            indexableToScan(this.Meta.keyDefinition, order.orders, equalPairs) { direction ->
                TableScan(
                    direction
                )
            }?.let {
                return it
            }

            // Walk all indices and try to match given Orders
            this.Meta.indices?.let { indices ->
                indexLoop@ for (indexable in indices) {
                    indexableToScan(indexable, order.orders, equalPairs) { direction ->
                        IndexScan(
                            indexable,
                            direction
                        )
                    }?.let {
                        return it
                    }
                }

                throw RequestException("No index match found on model ${this.Meta.name} for order $order")
            } ?: throw RequestException("No indices defined on model ${this.Meta.name} so order $order is not allowed")
        }
        else -> throw TypeException("Order type of $order is not supported")
    }
}

/** Convert Single [order] to ScanType */
private fun IsRootDataModel.singleOrderToScanType(
    order: Order
): ScanType {
    return if (order.propertyReference == null) {
        TableScan(order.direction)
    } else {
        singleIndexableToScan(this.Meta.keyDefinition, order) { direction ->
            TableScan(direction)
        }?.let {
            return it
        }

        this.Meta.indices?.let { indices ->
            for (indexable in indices) {
                singleIndexableToScan(indexable, order) { direction ->
                    IndexScan(indexable, direction)
                }?.let {
                    return it
                }
            }
        }

        throw RequestException("$order cannot be used as Order because there is no fitting index")
    }
}

/** Convert a single [indexable] to a scan using [createScan] matching [order] */
private fun singleIndexableToScan(
    indexable: IsIndexable,
    order: Order,
    createScan: (Direction) -> ScanType
) = when (indexable) {
    is Multiple ->
        when (val subIndexable = indexable.references[0]) {
            is Reversed<*> -> createSingleScan(subIndexable.reference, order, true, createScan)
            else -> createSingleScan(subIndexable, order, false, createScan)
        }
    is Reversed<*> -> createSingleScan(indexable.reference, order, true, createScan)
    is IsIndexablePropertyReference<*> -> createSingleScan(indexable, order, false, createScan)
    else -> throw TypeException("Indexable type of $indexable is not supported")
}

/** Creates with [createScan] a single scan matching [order] and [reversed] state */
private fun createSingleScan(
    subIndexable: IsIndexable,
    order: Order,
    reversed: Boolean,
    createScan: (Direction) -> ScanType
): ScanType? {
    return if (subIndexable == order.propertyReference) {
        val ascending = order.direction == ASC
        val direction = when {
            (ascending && !reversed) || (!ascending && reversed) -> ASC
            else -> DESC
        }
        createScan(direction)
    } else {
        null
    }
}

/**
 * Converts [indexable] from [orders] to scan created by [createScan]
 * and uses [equalPairs] to skip already known order parts
 */
private fun indexableToScan(
    indexable: IsIndexable,
    orders: List<Order>,
    equalPairs: List<ReferenceValuePair<*>>,
    createScan: (Direction) -> ScanType
): ScanType? {
    var direction: Direction? = null

    if (indexable !is Multiple) {
        return null // Non multiple indices should be catched with simple cases above
    } else if (orders.size > indexable.references.size + 1) {
        return null // If more orders than index size then skip
    } else if (orders.size > indexable.references.size && orders.last().propertyReference != null) {
        return null // If one more order than index size is not default order, then skip
    } else {
        var currentOrderIndex = 0

        // Walk all sub indexables inside Multiple
        subIndexLoop@ for ((index, subIndexable) in indexable.references.withIndex()) {
            val currentOrderPart = orders.getOrNull(currentOrderIndex++)
                ?: return createScan(
                    direction!!
                ) // direction is never null since 0 sized orders are skipped out early

            // When order is native Table order
            if (currentOrderPart.propertyReference == null) {
                when {
                    index != indexable.references.lastIndex ->
                        throw RequestException("An Order on Table is only allowed to be the last or only one")
                    currentOrderPart.direction == direction ->
                        createScan(direction)
                    else -> return null
                }
            }

            when (subIndexable) {
                is Reversed<*> -> {
                    // Only continue if order is correct
                    if (subIndexable.reference != currentOrderPart.propertyReference) {
                        if (equalPairs.any { it.reference == subIndexable.reference }) {
                            currentOrderIndex-- // substract because of a non order match
                        } else {
                            return null
                        }
                    } else {
                        when (direction) {
                            null -> direction = when (currentOrderPart.direction) {
                                ASC -> DESC
                                DESC -> ASC
                            }
                            ASC -> if (currentOrderPart.direction == ASC) return null else Unit
                            DESC -> if (currentOrderPart.direction == DESC) return null else Unit
                        }
                    }
                }
                else -> {
                    // Only continue if order is correct
                    if (subIndexable != currentOrderPart.propertyReference) {
                        if (equalPairs.any { it.reference == subIndexable }) {
                            currentOrderIndex-- // substract because of a non order match
                        } else {
                            return null
                        }
                    } else {
                        when (direction) {
                            null -> direction = when (currentOrderPart.direction) {
                                ASC -> ASC
                                DESC -> DESC
                            }
                            ASC -> if (currentOrderPart.direction == DESC) return null else Unit
                            DESC -> if (currentOrderPart.direction == ASC) return null else Unit
                        }
                    }
                }
            }
        }

        // There should be at least one order that matches, and thus direction should always be set
        if (currentOrderIndex == 0 || direction == null) return null

        // Catch check the last table order of indexable found before
        return when {
            currentOrderIndex == orders.size - 1 -> {
                val last = orders.last()
                if (last.propertyReference == null) {
                    when (direction) {
                        // Index match found
                        last.direction -> createScan(direction)
                        else -> throw RequestException("Cannot have a reversed Table order as last index parameter compared to index scan direction")
                    }
                } else return null
            }
            currentOrderIndex < orders.size -> return null
            else -> // Index match found
                createScan(direction)
        }
    }
}
