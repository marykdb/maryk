package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.DELETED_INDICATOR_ARRAY
import maryk.rocksdb.Transaction

/** Delete [keyAndReference] = [value] (ByteArray) at [version] for object at [key] */
internal fun deleteValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    keyAndReference: ByteArray,
    version: ByteArray
) {
    transaction.delete(
        columnFamilies.table,
        keyAndReference
    )

    if (columnFamilies is HistoricTableColumnFamilies) {
        val historicReference = byteArrayOf(*keyAndReference, *version)
        // Invert so the time is sorted in reverse order with newest on top
        historicReference.invert(historicReference.size - version.size)

        transaction.put(
            columnFamilies.historic.table,
            historicReference,
            DELETED_INDICATOR_ARRAY
        )
    }
}
