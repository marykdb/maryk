package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException
import kotlin.experimental.and
import kotlin.experimental.xor

/** Write the bytes of this UInt to a [writer] */
fun UInt.writeBytes(writer: (byte: Byte) -> Unit, length: Int = 4) {
    if (length !in 2..4) { throw IllegalArgumentException("Length should be within range of 3 to 4") }

    for (it in 0 until length) {
        val b = (this shr (length-1-it) * 8 and 0xFFu).toByte()
        writer(b)
    }
}

/** Creates Unsigned Integer by reading bytes from [reader] */
internal fun initUInt(reader: () -> Byte, length: Int = 4): UInt {
    var int = 0u
    val firstByte = reader()
    // Skip bytes if below certain length
    if (length < 4) {
        for (it in 0 until 8 - length) {
            int = int shl 8
        }
    }
    int = int xor ((firstByte).toUInt() and 0xFFu)
    for (it in 1 until length) {
        int = int shl 8
        int = int xor (reader().toUInt() and 0xFFu)
    }
    return int
}

/** Write the bytes of this UInt as a variable int to a [writer] */
internal fun UInt.writeVarBytes(writer: (byte: Byte) -> Unit) {
    var value = this
    while (true) {
        if (value and 0x7Fu.inv() == 0u) {
            writer(value.toByte())
            return
        } else {
            writer((value and 0x7Fu or 0x80u).toByte())
            value = value shr 7
        }
    }
}

/** Creates Unsigned Integer by reading bytes encoded with variable length from [reader] */
internal fun initUIntByVar(reader: () -> Byte): UInt {
    var shift = 0
    var result = 0u
    while (shift < 32) {
        val b = reader().toUInt()
        result = result or ((b and 0x7Fu) shl shift)
        if (b and 0x80u == 0u) {
            return result
        }
        shift += 7
    }
    throw ParseException("Malformed valInt")
}


/** Calculates the byte length of the variable unsigned int */
internal fun UInt.calculateVarByteLength(): Int = when {
    this and (UInt.MAX_VALUE shl 7) == 0u -> 1
    this and (UInt.MAX_VALUE shl 14) == 0u -> 2
    this and (UInt.MAX_VALUE shl 21) == 0u -> 3
    this and (UInt.MAX_VALUE shl 28) == 0u -> 4
    else -> 5
}

/** Write the key for ProtoBuf field */
internal fun UInt.writeVarIntWithExtraInfo(extraInfo: Byte, writer: (byte: Byte) -> Unit) {
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
internal fun <T> initUIntByVarWithExtraInfo(reader: () -> Byte, objectCreator: (UInt, Byte) -> T) : T {
    var byte = reader()

    val wireTypeByte = byte and 0b111

    var result = (byte and 0b0111_1000).toUInt() shr 3
    if (byte and SIGN_BYTE == ZERO_BYTE) {
        return objectCreator(result, wireTypeByte)
    }

    var shift = 4
    while (shift < 35) {
        byte = reader()
        result = result or ((byte and 0b0111_1111).toUInt() shl shift)
        if (byte and SIGN_BYTE == ZERO_BYTE) {
            return objectCreator(result, wireTypeByte)
        }
        shift += 7
    }
    throw ParseException("Too big tag")
}

/** Calculates the byte length of the variable int with extra info */
fun UInt.calculateVarIntWithExtraInfoByteSize(): Int = when {
    this and (UInt.MAX_VALUE shl 4) == 0u -> 1
    this and (UInt.MAX_VALUE shl 11) == 0u -> 2
    this and (UInt.MAX_VALUE shl 18) == 0u -> 3
    this and (UInt.MAX_VALUE shl 25) == 0u -> 4
    else -> 5
}
