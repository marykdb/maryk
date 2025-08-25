package maryk.datastore.foundationdb.processors.helpers

import maryk.lib.bytes.combineToByteArray

internal fun packKey(directory: ByteArray, vararg segments: ByteArray): ByteArray {
    return combineToByteArray(directory, *segments)
}
