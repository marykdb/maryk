package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.writeBytes
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.extensions.compare.matchPart
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction
import maryk.rocksdb.use

/**
 * Get a value for a [reference] from [columnFamilies] with [readOptions].
 * Depending on if [toVersion] is set it will be retrieved from the historic or current table.
 */
fun Transaction.get(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    toVersion: ULong?,
    reference: ByteArray
): ByteArray? {
    return if (toVersion == null) {
        this.get(columnFamilies.table, readOptions, reference)?.let {
            // Remove version from value
            it.copyOfRange(ULong.SIZE_BYTES, it.size)
        }
    } else {
        this.getIterator(readOptions).use { iterator ->
            val toSeek = reference.copyOf(reference.size + ULong.SIZE_BYTES)
            var writeIndex = reference.size
            toVersion.writeBytes({ toSeek[writeIndex++] = it })
            iterator.seek(toSeek)
            if (iterator.isValid()) {
                val key = iterator.key()
                if (key.matchPart(0, reference)) {
                    return iterator.value()
                }
            }
        }
        null
    }
}
