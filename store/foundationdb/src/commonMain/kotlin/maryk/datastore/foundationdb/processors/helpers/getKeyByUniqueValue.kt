package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Range
import com.apple.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.lib.extensions.compare.compareToWithOffsetLength

/**
 * Resolve a unique [reference] to its key bytes and the version it was set at.
 * When [toVersion] is set, reads from the historic unique index and returns the latest version <= toVersion.
 */
internal fun Transaction.getKeyByUniqueValue(
    tableDirs: IsTableDirectories,
    reference: ByteArray,
    toVersion: ULong?,
    handle: (keyBytes: ByteArray, setAtVersion: ULong) -> Unit
) {
    if (toVersion == null) {
        val value = this.get(packKey(tableDirs.uniquePrefix, reference)).join()
        if (value != null && value.size >= VERSION_BYTE_SIZE) {
            val setAtVersion = value.readVersionBytes()
            val keyBytes = value.copyOfRange(VERSION_BYTE_SIZE, value.size)
            handle(keyBytes, setAtVersion)
        }
    } else {
        val historic = tableDirs as? HistoricTableDirectories
            ?: throw RequestException("Cannot use toVersion on a non historic table")

        // Iterate within the unique reference prefix and select the first entry whose inverted version
        // is >= inverted(toVersion) (equivalent to version <= toVersion)
        val toVersionBytes = toVersion.toReversedVersionBytes()
        val prefix = packKey(historic.historicUniquePrefix, reference)
        val it = this.getRange(Range.startsWith(prefix)).iterator()
        while (it.hasNext()) {
            val kv = it.next()
            val versionOffset = kv.key.size - toVersionBytes.size
            if (toVersionBytes.compareToWithOffsetLength(kv.key, versionOffset) <= 0) {
                val version = kv.key.readReversedVersionBytes(versionOffset)
                val keyBytes = kv.value
                if (keyBytes.isNotEmpty()) {
                    handle(keyBytes, version)
                }
                break
            }
        }
    }
}
