package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.RequestException
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.extensions.compare.compareToWithOffsetLength
import maryk.lib.extensions.compare.matchPart
import maryk.lib.recyclableByteArray
import org.rocksdb.ReadOptions
import org.rocksdb.RocksDB

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
            valueLength == RocksDB.NOT_FOUND -> null
            valueLength > recyclableByteArray.size -> {
                handleResult(this.get(columnFamilies.table, readOptions, keyAndReference)!!,
                    VERSION_BYTE_SIZE, valueLength - VERSION_BYTE_SIZE
                )
            }
            else -> handleResult(recyclableByteArray,
                VERSION_BYTE_SIZE, valueLength - VERSION_BYTE_SIZE
            )
        }
    } else {
        val versionBytes = toVersion.toReversedVersionBytes()

        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("Cannot use toVersion on a non historic table")
        }

        this.getIterator(readOptions, columnFamilies.historic.table).use { iterator ->
            val toSeek = byteArrayOf(*keyAndReference, *versionBytes)
            iterator.seek(toSeek)
            while (iterator.isValid()) {
                val key = iterator.key()

                // Only continue if still same keyAndReference
                if (key.matchPart(0, keyAndReference)) {
                    val versionOffset = key.size - versionBytes.size
                    // Only match if version is valid, else read next version
                    if (versionBytes.compareToWithOffsetLength(key, versionOffset) <= 0) {
                        val result = iterator.value()
                        return handleResult(result, 0, result.size)
                    }
                } else break

                iterator.next()
            }
        }
        null
    }
}

