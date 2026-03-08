package maryk.datastore.shared

import maryk.core.exceptions.StorageException
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.Split
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.query.filters.And
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.Matches
import maryk.core.query.filters.Or
import maryk.core.query.orders.Direction.ASC
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan

/**
 * Try to find index scans for Table Scan since they can make the scan a lot shorter
 *
 * This compares on equal pairs for ordering indexes and named search matches for
 * search indexes. If user wants to scan over a specific ordering index, they should
 * use an Order to select that index.
 */
fun <DM: IsRootDataModel> DM.optimizeTableScan(
    tableScan: TableScan,
    keyScanRanges: KeyScanRanges,
    filter: IsFilter? = null,
    allowTableScan: Boolean = false,
): ScanType {
    val equalPairs = keyScanRanges.equalPairs
    this.Meta.indexes?.let { indexes ->
        indexWalk@ for (indexable in indexes) {
            when (indexable) {
                is Multiple -> {
                    for (subIndexable in indexable.references) {
                        if (subIndexable !is IsIndexablePropertyReference<*> ||
                            !equalPairs.any { subIndexable.isForPropertyReference(it.reference) }
                        ) {
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
                is AnyOf -> {
                    if (!hasSupportedSearchMatch(indexable, filter)) {
                        continue@indexWalk
                    }
                    return IndexScan(indexable, ASC)
                }
                is Split -> continue@indexWalk
                else -> throw TypeException("Indexable type of $indexable is not supported")
            }
        }
    }

    val minimumKeyScanByteRange = this.Meta.minimumKeyScanByteRange ?: this.Meta.keyByteSize.toUInt()

    if (!allowTableScan && keyScanRanges.equalBytes < minimumKeyScanByteRange) {
        throw StorageException("${this.Meta.name}: Key scan bytes (${keyScanRanges.equalBytes}) must be more or equal than minimum key scan bytes ($minimumKeyScanByteRange). Or set an order to guide scan to an index")
    }

    return tableScan
}

private fun hasSupportedSearchMatch(indexable: AnyOf, filter: IsFilter?): Boolean = when (filter) {
    null -> false
    is Matches -> filter.nameValuePairs.any { (name, _) -> indexable.name == name }
    is And -> filter.filters.any { hasSupportedSearchMatch(indexable, it) }
    is Or -> filter.filters.any { hasSupportedSearchMatch(indexable, it) }
    else -> false
}
