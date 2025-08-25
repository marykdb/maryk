package maryk.datastore.foundationdb.processors.helpers

import maryk.core.extensions.bytes.initULong
import maryk.core.extensions.bytes.toULong
import maryk.core.extensions.bytes.writeBytes
import kotlin.experimental.xor

internal const val VERSION_BYTE_SIZE = ULong.SIZE_BYTES

/** Create Version bytes out of a ULong */
internal fun ULong.toReversedVersionBytes() =
    ByteArray(VERSION_BYTE_SIZE).also { versionBytes ->
        var index = 0
        this.writeBytes({ versionBytes[index++] = it xor -1 }, VERSION_BYTE_SIZE)
    }

/**
 * Read version bytes from byte array
 */
internal fun ByteArray.readVersionBytes(offset: Int = 0) =
    this.toULong(offset, VERSION_BYTE_SIZE)

/**
 * Read reversed version bytes from byte array
 */
internal fun ByteArray.readReversedVersionBytes(offset: Int = 0): ULong {
    var readIndex = offset
    return initULong({
        // invert value before reading because is stored inverted
        this[readIndex++] xor -1
    }, VERSION_BYTE_SIZE)
}
