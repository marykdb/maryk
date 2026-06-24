package maryk.datastore.foundationdb.processors.helpers

import maryk.core.extensions.bytes.invert

internal fun packVersionedKey(directory: ByteArray, key: ByteArray, vararg segments: ByteArray, version: ByteArray): ByteArray =
    when (segments.size) {
        0 -> concatArrays(directory, key, 0.toByte(), version)
        1 -> concatArrays(directory, key, segments[0], 0.toByte(), version)
        else -> packKey(directory, key, *segments, byteArrayOf(0), version)
    }.also {
        it.invert(it.size - version.size)
    }
