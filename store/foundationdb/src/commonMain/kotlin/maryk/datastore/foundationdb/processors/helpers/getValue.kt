package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Range
import com.apple.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareToWithOffsetLength

/**
 * Get a value for a [keyAndReference] from [tableDirs].
 * If [toVersion] is provided, read from the historic table; when a [keyLength] is supplied,
 * the qualifier portion (after [keyLength]) is zero-free encoded to match the historic encoding.
 */
internal fun <T : Any> Transaction.getValue(
    tableDirs: IsTableDirectories,
    toVersion: ULong?,
    keyAndReference: ByteArray,
    keyLength: Int? = null,
    handleResult: (ByteArray, Int, Int) -> T?
): T? {
    return if (toVersion == null) {
        val packedKey = packKey(tableDirs.tablePrefix, keyAndReference)
        val value = this.get(packedKey).awaitResult() ?: return null
        handleResult(value, VERSION_BYTE_SIZE, value.size - VERSION_BYTE_SIZE)
    } else {
        val historicDirs = tableDirs as? HistoricTableDirectories
            ?: throw RequestException("Cannot use toVersion on a non historic table")

        val toVersionBytes = toVersion.toReversedVersionBytes()

        val encodedRef = if (keyLength != null && keyAndReference.size > keyLength) {
            val keyPart = keyAndReference.copyOfRange(0, keyLength)
            val qualPart = keyAndReference.copyOfRange(keyLength, keyAndReference.size)
            combineToByteArray(keyPart, encodeZeroFreeUsing01(qualPart))
        } else keyAndReference

        val prefixForKey = packKey(historicDirs.historicTablePrefix, encodedRef)

        FDBIterator(this.getRange(Range.startsWith(prefixForKey)).iterator()).use { iterator ->
            while (iterator.hasNext()) {
                val kv = iterator.next()
                val key = kv.key
                val versionOffset = key.size - toVersionBytes.size
                if (versionOffset <= 0) throw Exception("Invalid qualifier for versioned get Value")
                if (key[versionOffset - 1] != 0.toByte()) throw Exception("Missing separator in qualifier for versioned get Value")
                if (toVersionBytes.compareToWithOffsetLength(key, versionOffset) <= 0) {
                    val result = kv.value
                    return handleResult(result, 0, result.size)
                }
            }
        }
        null
    }
}
