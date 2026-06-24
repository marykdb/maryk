package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.RequestException
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.matchesRangePart
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions

/**
 * Get a unique record key by value
 */
internal fun getKeyByUniqueValue(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    reference: ByteArray,
    keySize: Int,
    toVersion: ULong?,
    processKey: (() -> Byte, ULong) -> Unit
) {
    if (toVersion == null) {
        val valueLength = dbAccessor.get(columnFamilies.unique, readOptions, reference, recyclableByteArray)
        if (valueLength == VERSION_BYTE_SIZE + keySize) {
            val value = if (valueLength > recyclableByteArray.size) {
                dbAccessor.get(columnFamilies.unique, readOptions, reference) ?: return
            } else {
                recyclableByteArray
            }
            val setAtVersion = value.readVersionBytes()
            var readIndex = VERSION_BYTE_SIZE
            processKey({ value[readIndex++] }, setAtVersion)
        }
    } else {
        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("Cannot use toVersion on a non historic table")
        }

        val versionBytes = toVersion.toReversedVersionBytes()

        dbAccessor.getIterator(readOptions, columnFamilies.historic.unique).use { iterator ->
            val toSeek = reference + versionBytes
            iterator.seek(toSeek)
            while (iterator.isValid()) {
                val key = iterator.key()

                // Only continue if still same keyAndReference
                if (key.matchesRangePart(0, reference)) {
                    val versionOffset = key.size - versionBytes.size
                    if (versionOffset != reference.size) {
                        iterator.next()
                        continue
                    }
                    // Only match if version is valid, else read next version
                    if (versionBytes.compareToRange(key, versionOffset) <= 0) {
                        val result = iterator.value()

                        if (result.size == keySize) {
                            var readIndex = 0
                            val resultReader = { result[readIndex++] }
                            val version = key.readReversedVersionBytes(versionOffset)
                            processKey(resultReader, version)
                            break
                        }

                        if (result.isEmpty()) {
                            break
                        }

                        // Keep scanning older versions for the latest visible value before toVersion.
                        iterator.next()
                        continue
                    }
                } else break

                iterator.next()
            }
        }
    }
}
