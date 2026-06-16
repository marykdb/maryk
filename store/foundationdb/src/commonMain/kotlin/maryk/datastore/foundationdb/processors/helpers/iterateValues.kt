package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.EMPTY_BYTEARRAY
import maryk.lib.extensions.compare.compareToRange

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
        val asyncIterable = this.getRange(Range.startsWith(packKey(tablePrefix, reference)))
        val it = FDBIterator(asyncIterable.iterator())

        while (it.hasNext()) {
            val kv = it.next()
            val referenceBytes = kv.key
            val value = kv.value
            val refOffset = tablePrefix.size + keyLength
            val refLength = referenceBytes.size - refOffset
            if (refLength < 0) continue
            requireVersionedValue(value)
            if (decryptValue == null) {
                handleValue(
                    referenceBytes,
                    refOffset,
                    refLength,
                    value,
                    VERSION_BYTE_SIZE,
                    value.size - VERSION_BYTE_SIZE
                )?.let { return it }
            } else {
                val payload = decryptValue(value, VERSION_BYTE_SIZE, value.size - VERSION_BYTE_SIZE)
                handleValue(
                    referenceBytes,
                    refOffset,
                    refLength,
                    payload,
                    0,
                    payload.size
                )?.let { return it }
            }
        }
        null
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
        val it = FDBIterator(this.getRange(Range.startsWith(packKey(histPrefix, encodedRef))).iterator())

        val toVersionBytes = toVersion.toReversedVersionBytes()

        while (it.hasNext()) {
            val kv = it.next()
            val keyBytes = kv.key
            val versionOffset = keyBytes.size - toVersionBytes.size
            val encodedQualifierOffset = histPrefix.size + keyLength
            val sepIndex = versionOffset - 1
            // Validate separator and ranges
            if (versionOffset <= 0 || sepIndex < encodedQualifierOffset || keyBytes[sepIndex] != 0.toByte()) {
                continue
            }
            val encodedQualifierLength = sepIndex - encodedQualifierOffset

            if (toVersionBytes.compareToRange(keyBytes, versionOffset) <= 0) {
                val value = kv.value
                val decodedQualifier = if (encodedQualifierLength > 0) {
                    decodeZeroFreeUsing01OrNull(keyBytes, encodedQualifierOffset, encodedQualifierLength) ?: continue
                } else EMPTY_BYTEARRAY
                if (decryptValue == null) {
                    handleValue(
                        decodedQualifier,
                        0,
                        decodedQualifier.size,
                        value,
                        0,
                        value.size
                    )?.let { return it }
                } else {
                    val decrypted = decryptValue(value, 0, value.size)
                    handleValue(
                        decodedQualifier,
                        0,
                        decodedQualifier.size,
                        decrypted,
                        0,
                        decrypted.size
                    )?.let { return it }
                }
            }
        }
        null
    }
}
