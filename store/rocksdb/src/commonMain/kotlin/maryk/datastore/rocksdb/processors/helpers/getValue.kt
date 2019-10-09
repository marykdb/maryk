package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.RequestException
import maryk.core.extensions.bytes.invert
import maryk.core.extensions.bytes.writeBytes
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.extensions.compare.compareToWithOffsetLength
import maryk.lib.extensions.compare.matchPart
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.use

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
        this.get(columnFamilies.table, readOptions, keyAndReference)?.let {
            handleResult(it, ULong.SIZE_BYTES, it.size - ULong.SIZE_BYTES)
        }
    } else {
        val versionBytes = toVersion.createReversedVersionBytes()

        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("Cannot use toVersion on a non historic table")
        }

        this.getIterator(readOptions, columnFamilies.historic.table).use { iterator ->
            val toSeek = keyAndReference.copyOf(keyAndReference.size + ULong.SIZE_BYTES)
            var writeIndex = keyAndReference.size
            toVersion.writeBytes({ toSeek[writeIndex++] = it })
            toSeek.invert(keyAndReference.size)
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

