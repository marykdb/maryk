package maryk.datastore.shared

import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootValuesDataModel
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.pairs.ReferenceValuePair

/**
 * Try to find index scans with the [equalPairs] for Table Scan since they can make the scan a lot shorter
 */
fun <DM: IsRootValuesDataModel<*>> DM.optimizeTableScan(
    tableScan: TableScan,
    equalPairs: List<ReferenceValuePair<*>>
): ScanType {
    this.indices?.let { indices ->
        indexWalk@ for (indexable in indices) {
            when (indexable) {
                is Multiple -> {
                    for (subIndexable in indexable.references) {
                        if (!equalPairs.any { subIndexable.isForPropertyReference(it.reference) }) {
                            continue@indexWalk // no equal pair found so continue
                        }
                    }
                    return IndexScan(indexable, ASC)
                }
                is Reversed<*> -> {
                    if (!equalPairs.any { indexable.isForPropertyReference(it.reference) }) {
                        continue@indexWalk // no equal pair found so continue
                    }
                    return IndexScan(indexable, ASC)
                }
                is IsIndexablePropertyReference<*> -> {
                    if (!equalPairs.any { indexable.isForPropertyReference(it.reference) }) {
                        continue@indexWalk // no equal pair found so continue
                    }
                    return IndexScan(indexable, ASC)
                }
                else -> throw TypeException("Indexable type of $indexable is not supported")
            }
        }
    }

    return tableScan
}
