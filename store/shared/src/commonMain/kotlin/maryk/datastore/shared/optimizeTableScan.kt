package maryk.datastore.shared

import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.pairs.ReferenceValuePair
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan

data class PartialIndexScanMatch(val score: UInt, val index: IsIndexable)

/**
 * Try to find index scans with the [equalPairs] for Table Scan since they can make the scan a lot shorter
 */
fun <DM: IsRootDataModel> DM.optimizeTableScan(
    tableScan: TableScan,
    equalPairs: List<ReferenceValuePair<*>>
): ScanType {
    this.Meta.indices?.let { indices ->
        val partialMatches = mutableListOf<PartialIndexScanMatch>()
        indexWalk@ for (indexable in indices) {
            when (indexable) {
                is Multiple -> {
                    var score = 0u
                    for (subIndexable in indexable.references) {
                        if (!equalPairs.any { subIndexable.isForPropertyReference(it.reference) }) {
                            if (score != 0u) {
                                partialMatches += PartialIndexScanMatch(score, indexable)
                            }
                            continue@indexWalk // no equal pair found so continue
                        }
                        score++
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
        if (partialMatches.isNotEmpty()) {
            return IndexScan(partialMatches.maxBy { it.score }.index, ASC)
        }
    }

    return tableScan
}
