package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.lib.bytes.combineToByteArray

/** Set [reference] = [value] (ByteArray) at [version] for object at [key] */
internal fun setValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    key: Key<*>,
    reference: ByteArray,
    version: ByteArray,
    value: ByteArray
) {
    transaction.put(
        columnFamilies.table,
        key.bytes + reference,
        version + value
    )

    if (columnFamilies is HistoricTableColumnFamilies) {
        val historicReference = combineToByteArray(key.bytes, reference, version)
        // Invert so the time is sorted in reverse order with newest on top
        historicReference.invert(historicReference.size - version.size)

        transaction.put(
            columnFamilies.historic.table,
            historicReference,
            value
        )
    }
}
