package maryk.datastore.rocksdb.processors

import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.rocksDBNotFound

/** Check if Object is soft deleted */
internal fun isSoftDeleted(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    toVersion: ULong?,
    key: ByteArray,
    keyOffset: Int = 0,
    keyLength: Int = key.size
): Boolean {
    val softDeleteQualifier = ByteArray(keyLength + 1)
    key.copyInto(softDeleteQualifier, 0, keyOffset, keyOffset + keyLength)
    softDeleteQualifier[keyLength] = SOFT_DELETE_INDICATOR

    val historicValue = dbAccessor.getValue(
        columnFamilies,
        readOptions,
        toVersion,
        softDeleteQualifier
    ) { b, o, l ->
        if (l <= 0) return@getValue true
        b[l + o - 1] == TRUE
    }
    if (historicValue != null || toVersion == null) {
        return historicValue == true
    }

    val valueBytes = maryk.lib.recyclableByteArray
    val length = dbAccessor.get(columnFamilies.table, readOptions, softDeleteQualifier, valueBytes)
    if (length == rocksDBNotFound || length <= 0) return false

    val version = valueBytes.readVersionBytes()
    val deleted = valueBytes[length - 1] == TRUE
    return deleted && version <= toVersion
}
