package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.RequestException
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.extensions.compare.compareToWithOffsetLength
import org.rocksdb.ReadOptions

/**
 * Get a value for a [reference] from [columnFamilies] with [readOptions].
 * Depending on if [toVersion] is set it will be retrieved from the historic or current table.
 */
internal fun <R: Any> DBAccessor.iterateValues(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    toVersion: ULong?,
    keyLength: Int,
    reference: ByteArray,
    handleValue: (ByteArray, Int, Int, ByteArray, Int, Int) -> R?
): R? {
    if (toVersion == null) {
        this.getIterator(readOptions, columnFamilies.table).use { iterator ->
            iterator.seek(reference)

            while (iterator.isValid()) {
                val referenceBytes = iterator.key()
                val value = iterator.value()
                handleValue(
                    referenceBytes, keyLength, referenceBytes.size - keyLength,
                    value,
                    VERSION_BYTE_SIZE, value.size - VERSION_BYTE_SIZE
                )?.let { return it }
                iterator.next()
            }
            return null
        }
    } else {
        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("Cannot use toVersion on a non historic table")
        }
        this.getIterator(readOptions, columnFamilies.historic.table).use { iterator ->
            val toVersionBytes = toVersion.toReversedVersionBytes()
            val toSeek = byteArrayOf(*reference, *toVersionBytes)
            iterator.seek(toSeek)
            while (iterator.isValid()) {
                val referenceBytes = iterator.key()
                val versionOffset = referenceBytes.size - toVersionBytes.size
                if (toVersionBytes.compareToWithOffsetLength(referenceBytes, versionOffset) <= 0) {
                    val value = iterator.value()
                    handleValue(
                        referenceBytes, keyLength, versionOffset,
                        value, 0, value.size
                    )?.let { return it }
                }
                iterator.next()
            }
            return null
        }
    }
}
