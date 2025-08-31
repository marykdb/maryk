package maryk.datastore.foundationdb.processors.helpers

import maryk.lib.extensions.compare.compareToWithOffsetLength
import maryk.lib.extensions.compare.match

/** Find historic qualifiers on [iterator] over [prefix] */
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
        val offset = prefix.size

        val toVersionBytes = toVersion.toReversedVersionBytes()

        var emitted = false
        while (hasNext()) {
            val value = next()
            // key range check is ensured with startsWith on the range
            val qualifier: ByteArray = value.key
            val versionOffset = qualifier.size - toVersionBytes.size

            val currentLastQualifier = lastQualifier
            if (currentLastQualifier != null && qualifier.match(offset, currentLastQualifier, versionOffset - offset, offset, lastQualifierLength)) {
                if (counter >= maxVersions)
                    continue // Already returned this qualifier max times, skip until new qualifier
            } else {
                counter = 0u
            }

            if (toVersionBytes.compareToWithOffsetLength(qualifier, versionOffset) <= 0) {
                lastQualifier = qualifier
                lastQualifierLength = versionOffset - offset

                // Return version. Invert it so it is in normal order
                handleVersion(
                    qualifier.readReversedVersionBytes(versionOffset)
                )

                resultHandler({ qualifier[offset + it] }, lastQualifierLength)
                counter++
                emitted = true
                break
            } else continue
        }

        emitted
    }
}
