package maryk.datastore.rocksdb.processors

import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.getValue
import org.rocksdb.ReadOptions

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

    return dbAccessor.getValue(
        columnFamilies,
        readOptions,
        toVersion,
        softDeleteQualifier
    ) { b, o, l ->
        b[l + o - 1] == TRUE
    } ?: false
}
