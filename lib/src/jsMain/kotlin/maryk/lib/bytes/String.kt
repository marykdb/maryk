package maryk.lib.bytes

import kotlin.experimental.and

@Suppress("UNUSED_PARAMETER")
actual fun fromCodePoint(value: Int) = js("String.fromCodePoint(value)") as String

@Suppress("UNUSED_PARAMETER")
private fun fromCharCode(value: Int) = js("String.fromCharCode(value)") as String

@Suppress("UNUSED_PARAMETER")
private fun fromCharCode(value: Int, value2: Int) = js("String.fromCharCode(value, value2)") as String

actual fun codePointAt(string: String, index: Int) = (js("string.codePointAt(index)") as Int)

private const val SEVEN_BYTES: Byte = 0b0111_1111.toByte()

actual fun initString(length: Int, reader: () -> Byte): String {
    var index = 0
    val read = {
        index++

        val v = reader()
        if (v < 0) {
            // If signed, change value to unsigned value
            (v and SEVEN_BYTES).toInt() + 0b1000_0000
        } else {
            v.toInt()
        }
    }

    var str = ""
    while (index < length) {
        val value = read()
        when {
            value < 0x80 -> str += fromCharCode(value)
            value in 192..223 -> str += fromCharCode(
                (value and 0x1F shl 6)
                        or (read() and 0x3F)
            )
            value in 224..239 -> str += fromCharCode(
                (value and 0x0F shl 12)
                        or (read() and 0x3F shl 6)
                        or (read() and 0x3F)
            )
            else -> {
                // surrogate pair
                val charCode = (
                        ((value and 0x07) shl 18)
                                or (read() and 0x3F shl 12)
                                or (read() and 0x3F shl 6)
                                or (read() and 0x3F)
                        ) - 0x010000
                str += fromCharCode(
                    (charCode shr 10) or 0xD800,
                    (charCode and 0x03FF) or 0xDC00
                )
            }
        }
    }
    return str
}
