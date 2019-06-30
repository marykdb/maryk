package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.core.extensions.bytes.writeBytes
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.extensions.compare.compareWithOffsetTo
import maryk.lib.extensions.compare.matchPart
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction
import maryk.rocksdb.use

/**
 * Get a value for a [reference] from [columnFamilies] with [readOptions].
 * Depending on if [toVersion] is set it will be retrieved from the historic or current table.
 */
fun <T: Any> Transaction.getValue(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    toVersion: ULong?,
    reference: ByteArray,
    handleResult: (ByteArray, Int, Int) -> T
): T? {
    return if (toVersion == null) {
        this.get(columnFamilies.table, readOptions, reference)?.let {
            handleResult(it, ULong.SIZE_BYTES, it.size - ULong.SIZE_BYTES)
        }
    } else {
        val versionBytes = toVersion.createReversedVersionBytes()

        this.getIterator(readOptions).use { iterator ->
            val toSeek = reference.copyOf(reference.size + ULong.SIZE_BYTES)
            var writeIndex = reference.size
            toVersion.writeBytes({ toSeek[writeIndex++] = it })
            toSeek.invert(reference.size)
            iterator.seek(toSeek)
            while (iterator.isValid()) {
                val key = iterator.key()

                // Only continue if still same reference
                if (key.matchPart(0, reference)) {
                    val versionOffset = key.size - versionBytes.size
                    // Only match if version is valid, else read next version
                    if (versionBytes.compareWithOffsetTo(key, versionOffset) <= 0) {
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

