package maryk.core.extensions.bytes

import maryk.lib.exceptions.ParseException

/** Write the bytes of this UInt to a [writer] */
fun UInt.writeBytes(writer: (byte: Byte) -> Unit, length: Int = 4) {
    if (length !in 3..4) { throw IllegalArgumentException("Length should be within range of 3 to 4") }

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
