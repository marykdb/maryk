package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Bytes
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.isSoftDeleted
import maryk.rocksdb.ReadOptions

internal class RequestKeySoftDeleteCache(
    private val dbAccessor: DBAccessor,
    private val columnFamilies: TableColumnFamilies,
    private val readOptions: ReadOptions,
    private val toVersion: ULong?,
    private val historicalReader: HistoricalTableReader? = null
) {
    private val softDeletedByKey = mutableMapOf<Bytes, Boolean>()
    private val creationVersionByKey = mutableMapOf<Bytes, ULong?>()

    fun get(
        key: ByteArray,
        keyOffset: Int = 0,
        keyLength: Int = key.size
    ): Boolean {
        val keyBytes = key.sliceKey(keyOffset, keyLength)
        return softDeletedByKey.getOrPut(Bytes(keyBytes)) {
            isSoftDeleted(dbAccessor, columnFamilies, readOptions, toVersion, key, keyOffset, keyLength, historicalReader)
        }
    }

    fun getCreationVersion(
        key: ByteArray,
        keyOffset: Int = 0,
        keyLength: Int = key.size
    ): ULong? {
        val keyBytes = key.sliceKey(keyOffset, keyLength)
        return creationVersionByKey.getOrPut(Bytes(keyBytes)) {
            readCreationVersion(dbAccessor, columnFamilies, readOptions, keyBytes, toVersion)
        }
    }

    private fun ByteArray.sliceKey(keyOffset: Int, keyLength: Int) =
        if (keyOffset == 0 && keyLength == this.size) {
            this
        } else {
            this.copyOfRange(keyOffset, keyOffset + keyLength)
        }
}
