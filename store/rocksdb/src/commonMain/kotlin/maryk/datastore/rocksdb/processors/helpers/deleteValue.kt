package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.shared.TypeIndicator

/** Delete [keyAndReference] at [version] for object */
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
            TypeIndicator.DeletedIndicator.byteArray,
        )
    }
}
