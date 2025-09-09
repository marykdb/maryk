package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException
import kotlin.experimental.and
import kotlin.experimental.xor

/** Write the bytes of this Int to a [writer] */
internal fun Int.writeBytes(writer: (byte: Byte) -> Unit, length: Int = 4) {
    if (length !in 3..4) {
        throw IllegalArgumentException("Length should be within range of 3 to 4")
    }

    for (it in 0 until length) {
        val b = (this shr (length - 1 - it) * 8 and 0xFF).toByte()
        writer(
            if (it == 0) b xor SIGN_BYTE else b
        )
    }
}

/** Creates Integer by reading bytes from [reader] */
internal fun initInt(reader: () -> Byte, length: Int = 4): Int {
    var int = 0
    val firstByte = reader()
    // Sign‑extend if shorter than 4 bytes: prefill with 0xFF for negatives, 0x00 otherwise,
    // then shift to make room for the remaining bytes.
    if (length < 4) {
        int = if (firstByte and SIGN_BYTE != SIGN_BYTE) -1 else 0
        int = int shl ((4 - length) * 8)
    }
    int = int xor ((firstByte xor SIGN_BYTE).toInt() and 0xFF)
    for (it in 1 until length) {
        int = int shl 8
        int = int xor (reader().toInt() and 0xFF)
    }
    return int
}

/**
 * Encodes the Int in zigzag pattern so negative values are
 * able to encode much more efficiently into varInt
 */
internal fun Int.encodeZigZag() = this shl 1 xor (this shr 31)

/** Decodes the Int out of zigzag pattern so bytes have the normal native order again */
internal fun Int.decodeZigZag() = this ushr 1 xor -(this and 1)

/** Write the bytes of this Int as a variable int to a [writer] */
fun Int.writeVarBytes(writer: (byte: Byte) -> Unit) {
    var value = this
    while (true) {
        if (value and 0x7F.inv() == 0) {
            writer(value.toByte())
            return
        } else {
            writer((value and 0x7F or 0x80).toByte())
            value = value ushr 7
        }
    }
}

/** Convert Int to byte array with variable encoding */
fun Int.toVarBytes() =
    ByteArray(this.calculateVarByteLength()).also { bytes ->
        var index = 0
        this.writeVarBytes {
            bytes[index++] = it
        }
    }

/** Creates Integer by reading bytes encoded with variable length from [reader] */
fun initIntByVar(reader: () -> Byte): Int {
    var shift = 0
    var result = 0
    while (shift < 32) {
        val b = reader().toInt()
        result = result or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) {
            return result
        }
        shift += 7
    }
    throw ParseException("Malformed varInt")
}

/** Calculates the byte length of the variable int */
fun Int.calculateVarByteLength(): Int = when {
    this and (Int.MAX_VALUE shl 7) == 0 -> 1
    this and (Int.MAX_VALUE shl 14) == 0 -> 2
    this and (Int.MAX_VALUE shl 21) == 0 -> 3
    this and (Int.MAX_VALUE shl 28) == 0 -> 4
    else -> 5
}

/** Write the bytes of this Int in little endian order to a [writer] */
internal fun Int.writeLittleEndianBytes(writer: (byte: Byte) -> Unit) {
    for (it in 0..3) {
        writer(
            (this shr it * 8 and 0xFF).toByte()
        )
    }
}

/** Creates Integer by reading bytes in little endian order from [reader] */
internal fun initIntLittleEndian(reader: () -> Byte) =
    (reader().toInt() and 0xff) or
    ((reader().toInt() and 0xff) shl 8) or
    ((reader().toInt() and 0xff) shl 16) or
    ((reader().toInt() and 0xff) shl 24)

/** Write the key for ProtoBuf field */
internal fun Int.writeVarIntWithExtraInfo(extraInfo: Byte, writer: (byte: Byte) -> Unit) {
    val byteSize = this.calculateVarIntWithExtraInfoByteSize()

    // Write Int (I) + Extra Info (X) + potential sign byte (S) (SIII IXXX)
    writer(
        (
            ((this shl 3).toByte() and 0b0111_1000) // Add first part of tag to byte
            xor (extraInfo and 0b111) // Add ExtraInfo to byte
        ) xor if (byteSize > 1) SIGN_BYTE else ZERO_BYTE // Add Sign byte if total is longer than 5 bytes
    )
    // Write any needed extra byte for the Int as a VarInt
    if (byteSize > 1) {
        for (it in 1 until byteSize) {
            val isLast = it == byteSize - 1
            writer(
                (this shr (7*it-3)).toByte() and SEVEN_BYTES xor if(isLast) ZERO_BYTE else SIGN_BYTE
            )
        }
    }
}

/**
 * Reads a var Int from [reader] with extra info encoded in last 3 bytes
 * which is forwarded as Int to [objectCreator] to create object of type [T] with first the normal Int
 * and then the extra info Int
 *
 * This is based on ProtoBuf encoding
 */
internal fun <T> initIntByVarWithExtraInfo(reader: () -> Byte, objectCreator: (Int, Byte) -> T): T {
    var byte = reader()

    val wireTypeByte = byte and 0b111

    var result = (byte and 0b0111_1000).toInt() shr 3
    if (byte and SIGN_BYTE == ZERO_BYTE) {
        return objectCreator(result, wireTypeByte)
    }

    var shift = 4
    while (shift < 35) {
        byte = reader()
        result = result or ((byte and 0b0111_1111).toInt() shl shift)
        if (byte and SIGN_BYTE == ZERO_BYTE) {
            return objectCreator(result, wireTypeByte)
        }
        shift += 7
    }
    throw ParseException("Too big tag")
}

/** Calculates the byte length of the variable int with extra info */
fun Int.calculateVarIntWithExtraInfoByteSize(): Int = when {
    this and (Int.MAX_VALUE shl 4) == 0 -> 1
    this and (Int.MAX_VALUE shl 11) == 0 -> 2
    this and (Int.MAX_VALUE shl 18) == 0 -> 3
    this and (Int.MAX_VALUE shl 25) == 0 -> 4
    else -> 5
}
