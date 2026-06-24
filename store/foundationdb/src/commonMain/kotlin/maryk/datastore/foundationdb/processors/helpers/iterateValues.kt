package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.EMPTY_BYTEARRAY
import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.matchesRange

/**
 * Iterate over values for a [reference] prefix with FoundationDB range reads.
 * If [toVersion] is provided, read from the historic table encoding where the
 * version is part of the key suffix (reversed for lexicographic ordering).
 *
 * [handleValue] receives bytes that contain the qualifier to inspect at
 * `[keyPrefixLength, keyPrefixLength + keySuffixLength)`, plus the value slice.
 * Return a non-null value to stop iteration and return it.
 */
internal fun <R : Any> Transaction.iterateValues(
    tableDirectories: IsTableDirectories,
    toVersion: ULong?,
    keyLength: Int,
    reference: ByteArray,
    decryptValue: DecryptValue? = null,
    handleValue: (ByteArray, Int, Int, ByteArray, Int, Int) -> R?
): R? {
    return if (toVersion == null) {
        val tablePrefix = tableDirectories.tablePrefix
        val scanRange = Range.startsWith(packKey(tablePrefix, reference))
        var nextBegin = scanRange.begin
        var matched: R? = null

        while (matched == null) {
            val result = forEachInRangeBatch(Range(nextBegin, scanRange.end), reverse = false) { kv ->
                val referenceBytes = kv.key
                val value = kv.value
                val refOffset = tablePrefix.size + keyLength
                val refLength = referenceBytes.size - refOffset
                if (refLength < 0) return@forEachInRangeBatch true
                requireVersionedValue(value)
                matched = if (decryptValue == null) {
                    handleValue(
                        referenceBytes,
                        refOffset,
                        refLength,
                        value,
                        VERSION_BYTE_SIZE,
                        value.size - VERSION_BYTE_SIZE
                    )
                } else {
                    val payload = decryptValue(value, VERSION_BYTE_SIZE, value.size - VERSION_BYTE_SIZE)
                    handleValue(
                        referenceBytes,
                        refOffset,
                        refLength,
                        payload,
                        0,
                        payload.size
                    )
                }
                matched == null
            }
            if (matched != null || result.completed || result.stoppedByCallback) break
            nextBegin = result.lastKey?.let { it + byteArrayOf(0) } ?: break
        }
        matched
    } else {
        // Historic values require a historic table. Keys are [reference]+[reversedVersion].
        if (tableDirectories !is HistoricTableDirectories) {
            throw RequestException("Cannot use toVersion on a non historic table")
        }
        val histPrefix = tableDirectories.historicTablePrefix
        // Encode qualifier part (after keyLength) to be zero-free for historic iteration
        val encodedRef = if (reference.size > keyLength) {
            encodeZeroFreeSuffixUsing01(reference, keyLength)
        } else reference
        val scanRange = Range.startsWith(packKey(histPrefix, encodedRef))
        var nextBegin = scanRange.begin

        val toVersionBytes = toVersion.toReversedVersionBytes()
        var lastEncodedQualifier: ByteArray? = null
        var lastEncodedQualifierLength = 0
        var matched: R? = null

        while (matched == null) {
            val result = forEachInRangeBatch(Range(nextBegin, scanRange.end), reverse = false) { kv ->
                val keyBytes = kv.key
                val versionOffset = keyBytes.size - toVersionBytes.size
                val encodedQualifierOffset = histPrefix.size + keyLength
                val sepIndex = versionOffset - 1
                // Validate separator and ranges
                if (versionOffset <= 0 || sepIndex < encodedQualifierOffset || keyBytes[sepIndex] != 0.toByte()) {
                    return@forEachInRangeBatch true
                }
                val encodedQualifierLength = sepIndex - encodedQualifierOffset

                if (toVersionBytes.compareToRange(keyBytes, versionOffset) <= 0) {
                    val previousQualifier = lastEncodedQualifier
                    if (previousQualifier != null &&
                        lastEncodedQualifierLength == encodedQualifierLength &&
                        keyBytes.matchesRange(
                            encodedQualifierOffset,
                            previousQualifier,
                            encodedQualifierLength,
                            encodedQualifierOffset,
                            lastEncodedQualifierLength
                        )
                    ) {
                        return@forEachInRangeBatch true
                    }
                    lastEncodedQualifier = keyBytes
                    lastEncodedQualifierLength = encodedQualifierLength

                    val value = kv.value
                    if (value.isHistoricDeleteMarker()) return@forEachInRangeBatch true
                    val decodedQualifier = if (encodedQualifierLength > 0) {
                        decodeZeroFreeUsing01OrNull(keyBytes, encodedQualifierOffset, encodedQualifierLength)
                            ?: return@forEachInRangeBatch true
                    } else EMPTY_BYTEARRAY
                    matched = if (decryptValue == null) {
                        handleValue(
                            decodedQualifier,
                            0,
                            decodedQualifier.size,
                            value,
                            0,
                            value.size
                        )
                    } else {
                        val decrypted = decryptValue(value, 0, value.size)
                        handleValue(
                            decodedQualifier,
                            0,
                            decodedQualifier.size,
                            decrypted,
                            0,
                            decrypted.size
                        )
                    }
                }
                matched == null
            }
            if (matched != null || result.completed || result.stoppedByCallback) break
            nextBegin = result.lastKey?.let { it + byteArrayOf(0) } ?: break
        }
        matched
    }
}
