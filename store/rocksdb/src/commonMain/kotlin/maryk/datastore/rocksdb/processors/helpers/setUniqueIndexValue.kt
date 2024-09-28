package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction

/** Set the [uniqueReferenceWithValue] to [key] for [version] */
internal fun setUniqueIndexValue(
    columnFamilies: TableColumnFamilies,
    transaction: Transaction,
    uniqueReferenceWithValue: ByteArray,
    version: ByteArray,
    key: Key<*>
) {
    transaction.put(columnFamilies.unique, uniqueReferenceWithValue, version + key.bytes)

    if (columnFamilies is HistoricTableColumnFamilies) {
        val historicReference = uniqueReferenceWithValue + version
        // Invert so the time is sorted in reverse order with newest on top
        historicReference.invert(historicReference.size - version.size)

        transaction.put(
            columnFamilies.historic.unique,
            historicReference,
            key.bytes
        )
    }
}
