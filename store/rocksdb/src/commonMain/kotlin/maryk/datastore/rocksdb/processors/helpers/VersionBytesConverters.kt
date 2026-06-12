package maryk.datastore.rocksdb.processors.helpers

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
internal fun ByteArray.readVersionBytes(offset: Int = 0): ULong {
    require(offset >= 0 && size - offset >= VERSION_BYTE_SIZE) {
        "Not enough bytes to read version at offset $offset from ${size} bytes"
    }
    return this.toULong(offset, VERSION_BYTE_SIZE)
}

internal fun ByteArray.readVersionBytesIfPresent(valueLength: Int, offset: Int = 0): ULong? {
    return if (valueLength - offset >= VERSION_BYTE_SIZE) {
        readVersionBytes(offset)
    } else {
        null
    }
}

/**
 * Read reversed version bytes from byte array
 */
internal fun ByteArray.readReversedVersionBytes(offset: Int = 0): ULong {
    require(offset >= 0 && size - offset >= VERSION_BYTE_SIZE) {
        "Not enough bytes to read reversed version at offset $offset from ${size} bytes"
    }
    var readIndex = offset
    return initULong({
        // invert value before reading because is stored inverted
        this[readIndex++] xor -1
    }, VERSION_BYTE_SIZE)
}
