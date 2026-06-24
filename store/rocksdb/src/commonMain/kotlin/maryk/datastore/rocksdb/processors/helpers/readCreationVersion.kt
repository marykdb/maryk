package maryk.datastore.rocksdb.processors.helpers

import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions

internal fun readCreationVersion(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray,
    toVersion: ULong? = null
): ULong? {
    if (toVersion != null && columnFamilies is HistoricTableColumnFamilies) {
        val historicValueLength = dbAccessor.get(columnFamilies.historic.table, readOptions, key, recyclableByteArray)
        recyclableByteArray.readVersionBytesIfExact(historicValueLength)?.let { return it }
    }

    val valueLength = dbAccessor.get(columnFamilies.keys, readOptions, key, recyclableByteArray)
    return recyclableByteArray.readVersionBytesIfExact(valueLength)
}
