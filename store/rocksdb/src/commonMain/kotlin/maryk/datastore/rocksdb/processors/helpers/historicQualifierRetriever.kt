package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.DBIterator
import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.matchesRange
import maryk.lib.extensions.compare.matchesRangePart
import maryk.lib.extensions.compare.nextByteInSameLength

/** Find historic qualifiers on [iterator] for [key] */
internal fun DBIterator.historicQualifierRetriever(
    key: Key<*>,
    toVersion: ULong,
    maxVersions: UInt,
    canSkipQualifier: (ByteArray, Int, Int) -> Boolean = { _, _, _ -> false },
    handleVersion: (ULong) -> Unit
): (((Int) -> Byte, Int) -> Unit) -> Boolean {
    var lastQualifier: ByteArray? = null
    var lastQualifierLength = 0
    var counter = 0u
    val toVersionBytes = toVersion.toReversedVersionBytes()
    var seekAfter: ByteArray? = null

    return { resultHandler ->
        val offset = key.size

        var found = false
        qualifierFinder@while (true) {
            val nextQualifier = seekAfter
            seekAfter = null
            if (nextQualifier == null) next() else seek(nextQualifier)
            if (!isValid()) {
                break // At end of iterator
            } else {
                val qualifier: ByteArray = key()
                if (!qualifier.matchesRangePart(0, key.bytes, length = key.size)) {
                    break
                }
                val versionOffset = qualifier.size - toVersionBytes.size
                if (versionOffset < offset) {
                    continue@qualifierFinder
                }
                val currentLastQualifier = lastQualifier
                if (currentLastQualifier != null && qualifier.matchesRange(offset, currentLastQualifier, versionOffset - offset, offset, lastQualifierLength)) {
                    if (counter >= maxVersions) {
                        continue@qualifierFinder // Already returned this qualifier so skip
                    }
                } else {
                    counter = 0u
                }

                if (toVersionBytes.compareToRange(qualifier, versionOffset) <= 0) {
                    found = true

                    lastQualifier = qualifier
                    lastQualifierLength = versionOffset - offset

                    // Return version. Invert it so it is in normal order
                    handleVersion(
                        qualifier.readReversedVersionBytes(versionOffset)
                    )

                    resultHandler({ qualifier[offset+it] }, lastQualifierLength)

                    counter++
                    // Prefix seeking is safe only for scalar leaves. Container prefixes
                    // also contain child qualifiers, which must remain visible.
                    if (counter >= maxVersions && canSkipQualifier(qualifier, offset, lastQualifierLength)) {
                        val prefix = qualifier.copyOfRange(0, versionOffset)
                        val end = prefix.nextByteInSameLength()
                        if (!end.contentEquals(prefix)) {
                            val endLength = end.indexOfLast { it != 0.toByte() } + 1
                            seekAfter = end.copyOf(endLength) + ByteArray(VERSION_BYTE_SIZE)
                        }
                    }

                    break
                } else {
                    if (canSkipQualifier(qualifier, offset, versionOffset - offset)) {
                        seekAfter = qualifier.copyOfRange(0, versionOffset) + toVersionBytes
                    }
                    continue
                }
            }
        }

        found
    }
}
