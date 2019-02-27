package maryk.core.processors.datastore

import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.query.orders.Direction

/** Type of scan to perform. On Table or one of the indices */
sealed class ScanType {
    /** Scan over the table in [direction] */
    data class TableScan(
        val direction: Direction
    ) : ScanType()

    /** Scan over [index] in [direction]*/
    data class IndexScan(
        val index: IsIndexable,
        val direction: Direction
    ) : ScanType()
}
