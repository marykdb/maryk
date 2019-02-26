package maryk.core.processors.datastore

import maryk.core.query.orders.Direction

sealed class ScanType {
    class StoreScan(val direction: Direction) : ScanType()
    object IndexScan : ScanType()
}
