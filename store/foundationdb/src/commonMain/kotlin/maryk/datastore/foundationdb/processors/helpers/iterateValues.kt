package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.Range
import com.apple.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
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
        val asyncIterable = this.getRange(Range.startsWith(packKey(histPrefix, reference)))
        val it = FDBIterator(asyncIterable.iterator())

        val toVersionBytes = toVersion.toReversedVersionBytes()

        while (it.hasNext()) {
            val kv = it.next()
            val referenceBytes = kv.key
            val versionOffset = referenceBytes.size - toVersionBytes.size
            val refOffset = histPrefix.size + keyLength
            val refLength = versionOffset - refOffset

            // Only accept keys with a version component <= requested (in reversed ordering)
            if (toVersionBytes.compareToWithOffsetLength(referenceBytes, versionOffset) <= 0) {
                val value = kv.value
                handleValue(
                    referenceBytes,
                    refOffset,
                    refLength,
                    value,
                    0,
                    value.size
                )?.let { return it }
            }
        }
        null
    }
}
