package maryk.datastore.rocksdb.processors

import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.HistoricalTableReader
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions

/** Check if Object is soft deleted */
internal fun isSoftDeleted(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    toVersion: ULong?,
    key: ByteArray,
    keyOffset: Int = 0,
    keyLength: Int = key.size,
    historicalTableReader: HistoricalTableReader? = null
): Boolean {
    val softDeleteQualifier = ByteArray(keyLength + 1)
    key.copyInto(softDeleteQualifier, 0, keyOffset, keyOffset + keyLength)
    softDeleteQualifier[keyLength] = SOFT_DELETE_INDICATOR

    // The latest marker is authoritative once its own version fits the snapshot,
    // even if a legacy store has an older historic marker or no matching row.
    if (toVersion != null && columnFamilies is HistoricTableColumnFamilies) {
        val value = recyclableByteArray
        val length = dbAccessor.get(columnFamilies.table, readOptions, softDeleteQualifier, value)
        if (length == ULong.SIZE_BYTES + 1 && value.readVersionBytes() <= toVersion) {
            return value[length - 1] == TRUE
        }
    }

    return dbAccessor.getValue(
        columnFamilies,
        readOptions,
        toVersion,
        softDeleteQualifier,
        historicalTableReader
    ) { b, o, l ->
        if (l != 1) return@getValue false
        b[l + o - 1] == TRUE
    } == true
}
