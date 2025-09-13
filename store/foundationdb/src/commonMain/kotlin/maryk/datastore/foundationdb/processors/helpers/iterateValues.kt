package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Range
import com.apple.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareToWithOffsetLength

/**
 * Iterate over values for a [reference] prefix with FoundationDB range reads.
 * If [toVersion] is provided, read from the historic table encoding where the
 * version is part of the key suffix (reversed for lexicographic ordering).
 *
 * [handleValue] receives (referenceBytes, keyPrefixLength, keySuffixLength, valueBytes, valueOffset, valueLength).
 * Return a non-null value to stop iteration and return it.
 */
internal fun <R : Any> Transaction.iterateValues(
    tableDirectories: IsTableDirectories,
    toVersion: ULong?,
    keyLength: Int,
    reference: ByteArray,
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
            handleValue(
                referenceBytes,
                refOffset,
                refLength,
                value,
                VERSION_BYTE_SIZE,
                value.size - VERSION_BYTE_SIZE
            )?.let { return it }
        }
        null
    } else {
        // Historic values require a historic table. Keys are [reference]+[reversedVersion].
        if (tableDirectories !is HistoricTableDirectories) {
            throw RequestException("Cannot use toVersion on a non historic table")
        }
        val histPrefix = tableDirectories.historicTablePrefix
        // Encode qualifier part (after keyLength) to be zero-free for historic iteration
        val keyPart = if (reference.size > keyLength) reference.copyOfRange(0, keyLength) else reference
        val encodedRef = if (reference.size > keyLength) {
            val qualPart = reference.copyOfRange(keyLength, reference.size)
            combineToByteArray(keyPart, encodeZeroFreeUsing01(qualPart))
        } else reference
        val baseRefBytes = combineToByteArray(histPrefix, keyPart)
        val it = FDBIterator(this.getRange(Range.startsWith(packKey(histPrefix, encodedRef))).iterator())

        val toVersionBytes = toVersion.toReversedVersionBytes()

        while (it.hasNext()) {
            val kv = it.next()
            val keyBytes = kv.key
            val versionOffset = keyBytes.size - toVersionBytes.size
            val refOffset = histPrefix.size + keyLength
            val sepIndex = versionOffset - 1
            // Validate separator and ranges
            if (versionOffset <= 0 || sepIndex < refOffset || keyBytes[sepIndex] != 0.toByte()) throw Exception("Invalid qualifier for versioned iterateValues")
            val encRefLength = sepIndex - refOffset

            if (toVersionBytes.compareToWithOffsetLength(keyBytes, versionOffset) <= 0) {
                val value = kv.value
                // Decode qualifier before handing to caller
                val decodedQualifier = if (encRefLength > 0) {
                    val encQual = keyBytes.copyOfRange(refOffset, sepIndex)
                    decodeZeroFreeUsing01(encQual)
                } else ByteArray(0)
                val refBytesForCaller = if (decodedQualifier.isNotEmpty()) combineToByteArray(
                    baseRefBytes,
                    decodedQualifier
                ) else baseRefBytes
                handleValue(
                    refBytesForCaller,
                    histPrefix.size + keyLength,
                    decodedQualifier.size,
                    value,
                    0,
                    value.size
                )?.let { return it }
            }
        }
        null
    }
}
