package maryk.datastore.foundationdb.processors.helpers

import maryk.lib.bytes.combineToByteArray

internal fun packKey(vararg segments: ByteArray): ByteArray =
    combineToByteArray(*segments)

internal fun packDescendingExclusiveEnd(includeStart: Boolean, vararg segments: ByteArray): ByteArray {
    val toCombine = if (includeStart) {
        Array(segments.size + 1) { index ->
            when {
                index < segments.size -> segments[index]
                else -> byteArrayOf(0)
            }
        }
    } else {
        segments
    }
    return combineToByteArray(*toCombine)
}
