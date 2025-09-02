package maryk.datastore.foundationdb.processors.helpers


import com.apple.foundationdb.Range
import com.apple.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.lib.extensions.compare.compareToWithOffsetLength

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
        val historicDirs = tableDirs as? HistoricTableDirectories
            ?: throw RequestException("Cannot use toVersion on a non historic table")

        val toVersionBytes = toVersion.toReversedVersionBytes()
        val prefixForKey = packKey(historicDirs.historicTablePrefix, keyAndReference)

        // Iterate all historic versions (ascending by inverted version) and select the
        // first one whose inverted version is >= inverted(toVersion), i.e. version <= toVersion.
        val it = this.getRange(Range.startsWith(prefixForKey)).iterator()
        while (it.hasNext()) {
            val kv = it.next()
            val key = kv.key
            val versionOffset = key.size - toVersionBytes.size
            if (versionOffset <= 0) throw Exception("Invalid qualifier for versioned get Value")
            if (key[versionOffset - 1] != 0.toByte()) throw Exception("Missing separator in qualifier for versioned get Value")
            if (toVersionBytes.compareToWithOffsetLength(key, versionOffset) <= 0) {
                val result = kv.value
                return handleResult(result, 0, result.size)
            }
        }
        null
    }
}
