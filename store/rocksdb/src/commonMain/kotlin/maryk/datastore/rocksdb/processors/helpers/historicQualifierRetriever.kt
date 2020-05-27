package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.DBIterator
import maryk.lib.extensions.compare.compareToWithOffsetLength
import maryk.lib.extensions.compare.match

/** Find historic qualifiers on [iterator] for [key] */
internal fun DBIterator.historicQualifierRetriever(
    key: Key<*>,
    toVersion: ULong,
    maxVersions: UInt,
    handleVersion: (ULong) -> Unit
): (((Int) -> Byte, Int) -> Unit) -> Boolean {
    var lastQualifier: ByteArray? = null
    var lastQualifierLength = 0
    var counter = 0u

    return { resultHandler ->
        val offset = key.size

        val toVersionBytes = toVersion.toReversedVersionBytes()

        var isValid = false
        qualifierFinder@while (isValid()) {
            next()
            if (!isValid()) {
                break // At end of iterator
            } else {
                // key range check is ensured with setPrefixSameAsStart
                val qualifier: ByteArray = key()
                val versionOffset = qualifier.size - toVersionBytes.size
                val currentLastQualifier = lastQualifier
                if (currentLastQualifier != null && qualifier.match(offset, currentLastQualifier, versionOffset - offset, offset, lastQualifierLength)) {
                    if (counter >= maxVersions) {
                        continue@qualifierFinder // Already returned this qualifier so skip
                    }
                } else {
                    counter = 0u
                }

                if (toVersionBytes.compareToWithOffsetLength(qualifier, versionOffset) <= 0) {
                    isValid = true

                    lastQualifier = qualifier
                    lastQualifierLength = versionOffset - offset

                    // Return version. Invert it so it is in normal order
                    handleVersion(
                        qualifier.readReversedVersionBytes(versionOffset)
                    )

                    resultHandler({ qualifier[offset+it] }, lastQualifierLength)

                    counter++

                    break
                } else continue
            }
        }

        isValid
    }
}
