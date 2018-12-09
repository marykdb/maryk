@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException

/** Write the bytes of this ULong to a [writer] */
internal fun ULong.writeBytes(writer: (byte: Byte) -> Unit, length: Int = 8) {
    if (length !in 5..8) { throw IllegalArgumentException("Length should be within range of 5 to 8") }

    for (it in 0 until length) {
        val b = (this shr (length-1-it) * 8 and 0xFFu).toByte()
        writer(b)
    }
}

/** Reads ULong from [reader] with bytes until [length] */
internal fun initULong(reader: () -> Byte, length: Int = 8): ULong {
    var long = 0uL
    // Skip bytes if below certain length
    if (length < 8) {
        for (it in 0 until 8 - length) {
            long = long shl 8
        }
    }
    for (it in 0 until length) {
        long = long shl 8
        long = long xor (reader().toULong() and 0xFFu)
    }
    return long
}

/** Write the bytes of this Long as a variable int to a [writer] */
internal fun ULong.writeVarBytes(writer: (byte: Byte) -> Unit) {
    var value = this
    while (true) {
        if (value and 0x7FuL.inv() == 0uL) {
            writer(value.toByte())
            return
        } else {
            writer((value and 0x7Fu or 0x80u).toByte())
            value = value shr 7
        }
    }
}

/** Reads Long represented by Variable Length from [reader] */
internal fun initULongByVar(reader: () -> Byte): ULong {
    var shift = 0
    var result = 0uL
    while (shift < 64) {
        val b = reader().toULong()
        result = result or ((b and 0x7FuL) shl shift)
        if (b and 0x80uL == 0uL) {
            return result
        }
        shift += 7
    }
    throw ParseException("Malformed varULong")
}

/** Calculates the byte length of the variable int */
internal fun ULong.calculateVarByteLength(): Int = when {
    this and (ULong.MAX_VALUE shl 7) == 0uL -> 1
    this and (ULong.MAX_VALUE shl 14) == 0uL -> 2
    this and (ULong.MAX_VALUE shl 21) == 0uL -> 3
    this and (ULong.MAX_VALUE shl 28) == 0uL -> 4
    this and (ULong.MAX_VALUE shl 35) == 0uL -> 5
    this and (ULong.MAX_VALUE shl 42) == 0uL -> 6
    this and (ULong.MAX_VALUE shl 49) == 0uL -> 7
    this and (ULong.MAX_VALUE shl 56) == 0uL -> 8
    this and (ULong.MAX_VALUE shl 63) == 0uL -> 9
    else -> 10
}
