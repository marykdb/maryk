package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.RequestException
import maryk.core.extensions.bytes.initULong
import maryk.core.extensions.bytes.invert
import maryk.core.extensions.bytes.writeBytes
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.extensions.compare.compareToWithOffsetLength
import maryk.lib.extensions.compare.matchPart
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction
import maryk.rocksdb.use
import kotlin.experimental.xor

/**
 * Get a unique record key by value
 */
fun getKeyByUniqueValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    reference: ByteArray,
    toVersion: ULong?,
    processKey: (() -> Byte, ULong) -> Unit
) {
    if (toVersion == null) {
        transaction.get(columnFamilies.unique, readOptions, reference)?.let { versionAndKey ->
            var readIndex = 0
            val versionAndKeyReader = { versionAndKey[readIndex++] }
            val setAtVersion = initULong(versionAndKeyReader)
            processKey(versionAndKeyReader, setAtVersion)
        }
    } else {
        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("Cannot use toVersion on a non historic table")
        }

        val versionBytes = toVersion.createReversedVersionBytes()

        transaction.getIterator(readOptions, columnFamilies.historic.unique).use { iterator ->
            val toSeek = reference.copyOf(reference.size + ULong.SIZE_BYTES)
            var writeIndex = reference.size
            toVersion.writeBytes({ toSeek[writeIndex++] = it })
            toSeek.invert(reference.size)
            iterator.seek(toSeek)
            while (iterator.isValid()) {
                val key = iterator.key()

                // Only continue if still same keyAndReference
                if (key.matchPart(0, reference)) {
                    val versionOffset = key.size - versionBytes.size
                    // Only match if version is valid, else read next version
                    if (versionBytes.compareToWithOffsetLength(key, versionOffset) <= 0) {
                        val result = iterator.value()
                        var readIndex = 0
                        val resultReader = { result[readIndex++] }
                        var versionReadIndex = versionOffset
                        val version = initULong({ key[versionReadIndex++] xor -1 })
                        processKey(resultReader, version)
                    }
                } else break

                iterator.next()
            }
        }
    }
}
