package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.KeySelector
import com.apple.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories

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

        val versionBytes = toVersion.toReversedVersionBytes()
        val lower = packKey(historic.historicUniquePrefix, reference)
        val upperIncl = packKey(historic.historicUniquePrefix, reference, versionBytes)

        val beginSel = KeySelector.firstGreaterOrEqual(lower)
        val endSel = KeySelector.lastLessOrEqual(upperIncl)

        val kvs = this.getRange(beginSel, endSel, 1, true).asList().join()
        if (kvs.isNotEmpty()) {
            val kv = kvs[0]
            val versionOffset = kv.key.size - versionBytes.size
            val version = kv.key.readReversedVersionBytes(versionOffset)

            val keyBytes = kv.value
            if (keyBytes.isNotEmpty()) {
                handle(keyBytes, version)
            }
        }
    }
}

