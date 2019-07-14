package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.Transaction

/** Set [reference] = [value] (ByteArray) at [version] for object at [key] */
internal fun setValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    keyAndReference: ByteArray,
    version: ByteArray,
    value: ByteArray,
    valueOffset: Int = 0,
    valueLength: Int = value.size - valueOffset
) {
    val valueWithVersion = ByteArray(ULong.SIZE_BYTES + valueLength)
    version.copyInto(valueWithVersion)
    value.copyInto(valueWithVersion, 8, valueOffset, valueOffset + valueLength)

    transaction.put(
        columnFamilies.table,
        keyAndReference,
        valueWithVersion
    )

    if (columnFamilies is HistoricTableColumnFamilies) {
        val historicReference = byteArrayOf(*keyAndReference, *version)
        // Invert so the time is sorted in reverse order with newest on top
        historicReference.invert(historicReference.size - version.size)

        val valueBytes = if (value.size != valueLength) {
            ByteArray(valueLength).apply {
                value.copyInto(valueWithVersion, 0, valueOffset, valueOffset + valueLength)
            }
        } else value

        transaction.put(
            columnFamilies.historic.table,
            historicReference,
            valueBytes
        )
    }
}
