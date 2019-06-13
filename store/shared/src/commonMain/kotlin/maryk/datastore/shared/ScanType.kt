package maryk.datastore.shared

import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.ASC

/** Type of scan to perform. On Table or one of the indices */
sealed class ScanType {
    abstract val direction: Direction

    /** Scan over the table in [direction] */
    data class TableScan(
        override val direction: Direction = ASC
    ) : ScanType()

    /** Scan over [index] in [direction]*/
    data class IndexScan(
        val index: IsIndexable,
        override val direction: Direction
    ) : ScanType()
}
