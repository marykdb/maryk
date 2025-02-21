package maryk.datastore.shared

import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.scanRange.IndexableScanRanges
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.query.filters.IsFilter
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan

data class IndexScanMatch(val score: Int, val scan: ScanType, val indexableScanRanges: IndexableScanRanges?)

/**
 * Try to find index scans with the [filter] and [keyScanRange] for Table Scan since they can make the scan a lot shorter
 */
fun <DM: IsRootDataModel> DM.optimizeTableScan(
    tableScan: TableScan,
    filter: IsFilter?,
    keyScanRange: KeyScanRanges,
): Pair<ScanType, IndexableScanRanges?> {
    val potentialIndexScanMatches = mutableListOf<IndexScanMatch>()
    this.Meta.indices?.let { indices ->
        indexWalk@ for (indexable in indices) {
            val indexScanRange = indexable.createScanRange(filter, keyScanRange)

            val firstRange = indexScanRange.ranges.firstOrNull()

            if (firstRange != null) {
                val endSize = firstRange.end?.size ?: 0
                if (firstRange.start.size > 0 || endSize > 0) {
                    firstRange.start.size + endSize
                    potentialIndexScanMatches.add(
                        IndexScanMatch(
                            score = firstRange.start.size + endSize, // score is the start + end length to have the most limiting bandwidth
                            scan = IndexScan(
                                indexable,
                                if (firstRange.start.size >= endSize) ASC else Direction.DESC
                            ),
                            indexableScanRanges = indexScanRange
                        )
                    )
                }
            }
        }
        if (potentialIndexScanMatches.isNotEmpty()) {
            return potentialIndexScanMatches.maxBy { it.score }.let { it.scan to it.indexableScanRanges }
        }
    }

    return tableScan to null
}
