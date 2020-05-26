package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.LAST_VERSION_INDICATOR
import maryk.lib.recyclableByteArray
import maryk.lib.recyclableByteArray2
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.rocksDBNotFound

/**
 * Get last version for given key to compare with. Object should exist, or it throws an exception.
 */
internal fun <DM: IsRootDataModel<*>> getLastVersion(dbAccessor: DBAccessor, columnFamilies: TableColumnFamilies, readOptions: ReadOptions, key: Key<DM>): ULong {
    key.bytes.copyInto(recyclableByteArray)
    recyclableByteArray[key.bytes.size] = LAST_VERSION_INDICATOR

    val valueLength = dbAccessor.get(columnFamilies.table, readOptions, recyclableByteArray, 0, key.size + 1, recyclableByteArray2)

    if (valueLength == rocksDBNotFound) {
        throw StorageException("Can only retrieve last versions of existing objects")
    }

    return recyclableByteArray2.readVersionBytes()
}
