package maryk.datastore.rocksdb.processors.helpers

import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions

internal fun readCreationVersion(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray
): ULong? {
    val valueLength = dbAccessor.get(columnFamilies.table, readOptions, key, recyclableByteArray)
    return recyclableByteArray.readVersionBytesIfPresent(valueLength)
}
