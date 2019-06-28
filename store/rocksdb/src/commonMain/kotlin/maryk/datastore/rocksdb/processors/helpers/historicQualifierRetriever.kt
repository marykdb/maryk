package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.initULong
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.processors.LAST_VERSION_INDICATOR
import maryk.lib.extensions.compare.compareWithOffsetTo
import maryk.lib.extensions.compare.matchPart
import maryk.rocksdb.RocksIterator
import kotlin.experimental.xor

/** Find historic qualifiers on [iterator] for [key] */
fun RocksIterator.historicQualifierRetriever(
    key: Key<*>,
    toVersion: ULong,
    handleVersion: (ULong) -> Unit
): () -> ByteArray? {
    var lastQualifier: ByteArray? = null

    val toVersionBytes = toVersion.createReversedVersionBytes()

    return {
        var toReturn: ByteArray? = null
        qualifierFinder@while (isValid()) {
            next()
            if (!isValid()) {
                break // At end of iterator
            } else {
                // key range check is ensured with setPrefixSameAsStart
                val qualifier: ByteArray = key()
                val qualifierToCheck = lastQualifier
                if (qualifierToCheck != null && qualifier.matchPart(key.size, qualifierToCheck)) {
                    continue@qualifierFinder // Already returned this qualifier so skip
                }

                // Skip last version indicator
                if (qualifier[key.size] == LAST_VERSION_INDICATOR) {
                    continue
                }

                var versionOffset = qualifier.size - toVersionBytes.size
                if (toVersionBytes.compareWithOffsetTo(qualifier, versionOffset) <= 0) {
                    toReturn = qualifier.copyOfRange(key.bytes.size, versionOffset)

                    // Return version. Invert it so it is in normal order
                    handleVersion(
                        initULong({ qualifier[versionOffset++] xor -1 })
                    )

                    lastQualifier = toReturn
                    break
                } else continue
            }
        }
        toReturn
    }
}
