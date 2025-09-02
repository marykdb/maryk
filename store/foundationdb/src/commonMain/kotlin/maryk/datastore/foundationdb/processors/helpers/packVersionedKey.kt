package maryk.datastore.foundationdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.lib.bytes.combineToByteArray

internal fun packVersionedKey(directory: ByteArray, key: ByteArray, vararg segments: ByteArray, version: ByteArray): ByteArray =
    combineToByteArray(directory, key, *segments, 0.toByte(), version).let {
        it.invert(it.size - version.size)
    }
