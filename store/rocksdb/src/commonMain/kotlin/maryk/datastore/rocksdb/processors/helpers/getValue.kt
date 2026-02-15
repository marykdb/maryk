package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.RequestException
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.matchesRangePart
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.rocksDBNotFound

/**
 * Get a value for a [keyAndReference] from [columnFamilies] with [readOptions].
 * Depending on if [toVersion] is set it will be retrieved from the historic or current table.
 */
internal fun <T: Any> DBAccessor.getValue(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    toVersion: ULong?,
    keyAndReference: ByteArray,
    handleResult: (ByteArray, Int, Int) -> T?
): T? {
    return if (toVersion == null) {
        val valueLength = this.get(columnFamilies.table, readOptions, keyAndReference, recyclableByteArray)

        when {
            valueLength == rocksDBNotFound -> null
            valueLength > recyclableByteArray.size -> {
                val valueBytes = this.get(columnFamilies.table, readOptions, keyAndReference)!!
                val decrypted = this.dataStore.decryptValueIfNeeded(valueBytes.copyOfRange(VERSION_BYTE_SIZE, valueLength))
                handleResult(decrypted, 0, decrypted.size)
            }
            else -> {
                val decrypted = this.dataStore.decryptValueIfNeeded(
                    recyclableByteArray.copyOfRange(VERSION_BYTE_SIZE, valueLength)
                )
                handleResult(decrypted, 0, decrypted.size)
            }
        }
    } else {
        val versionBytes = toVersion.toReversedVersionBytes()

        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("Cannot use toVersion on a non historic table")
        }

        this.getIterator(readOptions, columnFamilies.historic.table).use { iterator ->
            val toSeek = keyAndReference + versionBytes
            iterator.seek(toSeek)
            while (iterator.isValid()) {
                val key = iterator.key()

                // Only continue if still same keyAndReference
                if (key.matchesRangePart(0, keyAndReference)) {
                    val versionOffset = key.size - versionBytes.size
                    // Only match if version is valid, else read next version
                    if (versionBytes.compareToRange(key, versionOffset) <= 0) {
                        val result = iterator.value()
                        val decrypted = this.dataStore.decryptValueIfNeeded(result)
                        return handleResult(decrypted, 0, decrypted.size)
                    }
                } else break

                iterator.next()
            }
        }
        null
    }
}
