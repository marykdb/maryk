package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.initULong
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.DBIterator
import maryk.lib.extensions.compare.compareToWithOffsetLength
import maryk.lib.extensions.compare.matchPart
import kotlin.experimental.xor

/** Find historic qualifiers on [iterator] for [key] */
internal fun DBIterator.historicQualifierRetriever(
    key: Key<*>,
    toVersion: ULong,
    handleVersion: (ULong) -> Unit
): (((Int) -> Byte, Int) -> Unit) -> Boolean = { resultHandler ->
    var lastQualifier: ByteArray? = null
    val offset = key.size
    var lastQualifierLength = 0

    val toVersionBytes = toVersion.createReversedVersionBytes()

    var isValid = false
    qualifierFinder@while (isValid()) {
        next()
        if (!isValid()) {
            break // At end of iterator
        } else {
            // key range check is ensured with setPrefixSameAsStart
            val qualifier: ByteArray = key()
            var versionOffset = qualifier.size - toVersionBytes.size
            if (lastQualifier != null && qualifier.matchPart(key.size, lastQualifier, versionOffset, offset, lastQualifierLength)) {
                continue@qualifierFinder // Already returned this qualifier so skip
            }

            if (toVersionBytes.compareToWithOffsetLength(qualifier, versionOffset) <= 0) {
                isValid = true

                @Suppress("UNUSED_VALUE")
                lastQualifier = qualifier
                lastQualifierLength = versionOffset - offset

                // Return version. Invert it so it is in normal order
                handleVersion(
                    initULong({ qualifier[versionOffset++] xor -1 })
                )

                resultHandler({ qualifier[offset+it] }, lastQualifierLength)
                break
            } else continue
        }
    }

    isValid
}
