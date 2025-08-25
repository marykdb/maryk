package maryk.datastore.foundationdb.processors.helpers


import com.apple.foundationdb.KeySelector
import com.apple.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories

/**
 * Get a value for a [keyAndReference] from [tableDirs].
 * Depending on if [toVersion] is set it will be retrieved from the historic or current table.
 */
internal fun <T: Any> Transaction.getValue(
    tableDirs: IsTableDirectories,
    toVersion: ULong?,
    keyAndReference: ByteArray,
    handleResult: (ByteArray, Int, Int) -> T?
): T? {
    return if (toVersion == null) {
        val packedKey = packKey(tableDirs.tablePrefix, keyAndReference)
        val value = this.get(packedKey).join() ?: return null

        handleResult(value, VERSION_BYTE_SIZE, value.size - VERSION_BYTE_SIZE)
    } else {
        val versionBytes = toVersion.toReversedVersionBytes()

        val historicDirs = tableDirs as? HistoricTableDirectories
            ?: throw RequestException("Cannot use toVersion on a non historic table")

        // We want the latest version <= toVersion.
        // Build range over all versions for the specific keyAndReference and cap the end at toVersion.
        val prefixForKey = packKey(historicDirs.historicTablePrefix, keyAndReference)
        val upperBoundIncl = packKey(historicDirs.historicTablePrefix, keyAndReference, versionBytes)

        // Create selectors for [prefixForKey, upperBoundIncl], then read 1 kv in reverse to get the latest <= toVersion.
        val beginSel = KeySelector.firstGreaterOrEqual(prefixForKey)
        val endSel = KeySelector.lastLessOrEqual(upperBoundIncl)

        val kvs = this.getRange(beginSel, endSel, 1, true).asList().join()
        if (kvs.isEmpty()) return null

        val result = kvs[0].value
        handleResult(result, 0, result.size)
    }
}

