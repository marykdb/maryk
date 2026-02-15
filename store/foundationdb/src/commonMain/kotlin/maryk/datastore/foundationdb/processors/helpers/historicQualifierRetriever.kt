package maryk.datastore.foundationdb.processors.helpers

import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.matchesRange

/**
 * Historic qualifier retriever without extra buffering.
 * With zero-free encoded qualifiers, the base qualifier (ending before the 0 separator)
 * always sorts before any deeper (longer) qualifiers at the same path, so iteration order is correct.
 */
internal fun FDBIterator.historicQualifierRetriever(
    prefix: ByteArray,
    toVersion: ULong,
    maxVersions: UInt,
    handleVersion: (ULong) -> Unit
): (((Int) -> Byte, Int) -> Unit) -> Boolean {
    var lastQualifier: ByteArray? = null
    var lastQualifierLength = 0
    var counter = 0u

    return { resultHandler ->
        val toVersionBytes = toVersion.toReversedVersionBytes()
        var emitted = false
        while (hasNext()) {
            val kv = next()
            val key = kv.key
            val offset = prefix.size
            if (key.size <= offset + 1 + toVersionBytes.size) continue
            val versionOffset = key.size - toVersionBytes.size
            val sepIndex = versionOffset - 1
            if (sepIndex < offset || key[sepIndex] != 0.toByte()) continue

            // Compare with last emitted qualifier (encoded form) to enforce maxVersions
            val currentLast = lastQualifier
            if (currentLast != null && key.matchesRange(offset, currentLast, sepIndex - offset, offset, lastQualifierLength)) {
                if (counter >= maxVersions) continue
            } else {
                counter = 0u
            }

            if (toVersionBytes.compareToRange(key, versionOffset) <= 0) {
                // Decode qualifier slice before returning it
                val encodedQualifier = key.copyOfRange(offset, sepIndex)
                val decodedQualifier = decodeZeroFreeUsing01(encodedQualifier)
                lastQualifier = key
                lastQualifierLength = sepIndex - offset
                handleVersion(key.readReversedVersionBytes(versionOffset))
                resultHandler({ i -> decodedQualifier[i] }, decodedQualifier.size)
                counter++
                emitted = true
                break
            }
        }
        emitted
    }
}
