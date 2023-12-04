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
 * Get a unique record key by value
 */
internal fun getKeyByUniqueValue(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    reference: ByteArray,
    toVersion: ULong?,
    processKey: (() -> Byte, ULong) -> Unit
) {
    if (toVersion == null) {
        val valueLength = dbAccessor.get(columnFamilies.unique, readOptions, reference, recyclableByteArray)
        if (valueLength != RocksDB.NOT_FOUND) {
            val setAtVersion = recyclableByteArray.readVersionBytes()
            var readIndex = VERSION_BYTE_SIZE
            processKey({ recyclableByteArray[readIndex++] }, setAtVersion)
        }
    } else {
        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("Cannot use toVersion on a non historic table")
        }

        val versionBytes = toVersion.toReversedVersionBytes()

        dbAccessor.getIterator(readOptions, columnFamilies.historic.unique).use { iterator ->
            val toSeek = byteArrayOf(*reference, *versionBytes)
            iterator.seek(toSeek)
            while (iterator.isValid()) {
                val key = iterator.key()

                // Only continue if still same keyAndReference
                if (key.matchPart(0, reference)) {
                    val versionOffset = key.size - versionBytes.size
                    // Only match if version is valid, else read next version
                    if (versionBytes.compareToWithOffsetLength(key, versionOffset) <= 0) {
                        val result = iterator.value()

                        // Only process key if value was not unset at this version
                        // It was invalid if version was added after the key
                        if (result.isNotEmpty()) {
                            var readIndex = 0
                            val resultReader = { result[readIndex++] }
                            val version = key.readReversedVersionBytes(versionOffset)
                            processKey(resultReader, version)
                        }
                        break
                    }
                } else break

                iterator.next()
            }
        }
    }
}
