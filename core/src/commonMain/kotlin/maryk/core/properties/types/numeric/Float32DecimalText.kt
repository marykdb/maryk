package maryk.core.properties.types.numeric

import kotlin.math.abs
import kotlin.math.pow

/** Avoid Double rounding when JS parses decimal text or a Long into Float32. */
internal fun parseFloat32DecimalText(text: String): Float {
    val number = text.toDouble()
    val candidate = number.toFloat().atFloat32Precision()
    val parsed = abs(number)
    if (!parsed.isFinite()) return candidate
    val raw = candidate.toRawBits()
    val bits = raw and Int.MAX_VALUE

    // Double parsing can change the Float32 result only when it lands exactly
    // on a Float32 midpoint. Ordinary inputs use the native parser's fast path.
    val lower = if (bits > 0) float32Midpoint(bits - 1) else null
    if (lower != null && parsed == lower.asDouble()) {
        val input = TextMagnitude.parse(text) ?: return candidate
        val comparison = input.compareTo(lower.magnitude(input.binary))
        if (comparison < 0 || comparison == 0 && bits and 1 != 0) {
            return Float.fromBits((bits - 1) or (raw and Int.MIN_VALUE))
        }
    }
    if (bits < 0x7f800000) {
        val upper = float32Midpoint(bits)
        if (parsed == upper.asDouble()) {
            val input = TextMagnitude.parse(text) ?: return candidate
            val comparison = input.compareTo(upper.magnitude(input.binary))
            if (comparison > 0 || comparison == 0 && bits and 1 != 0) {
                return Float.fromBits((bits + 1) or (raw and Int.MIN_VALUE))
            }
        }
    }
    return candidate
}

/** The boundary above a finite positive Float32, including zero and Float.MAX_VALUE. */
private fun float32Midpoint(bits: Int): BinaryMidpoint {
    val exponent = bits ushr 23
    val significand = (bits and 0x7fffff) or if (exponent == 0) 0 else 0x800000
    return BinaryMidpoint(2 * significand + 1, if (exponent == 0) -150 else exponent - 151)
}

private data class BinaryMidpoint(val significand: Int, val exponent: Int) {
    fun asDouble() = significand.toDouble() * 2.0.pow(exponent)

    fun magnitude(binary: Boolean): TextMagnitude {
        if (binary) {
            val digits = significand.toString(2)
            return TextMagnitude(digits.trimEnd('0'), digits.length.toLong() + exponent, true)
        }
        var digits = significand.toString()
        repeat(abs(exponent)) { digits = digits.multiplySmall(if (exponent < 0) 5 else 2) }
        return TextMagnitude(digits.trimEnd('0'), digits.length.toLong() + minOf(exponent, 0), false)
    }
}

/** Significant digits and radix order permit comparison without enormous exponent allocations. */
private data class TextMagnitude(val digits: String, val order: Long, val binary: Boolean) : Comparable<TextMagnitude> {
    override fun compareTo(other: TextMagnitude): Int {
        if (order != other.order) return order.compareTo(other.order)
        for (index in 0 until maxOf(digits.length, other.digits.length)) {
            val comparison = (digits.getOrNull(index) ?: '0').compareTo(other.digits.getOrNull(index) ?: '0')
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        fun parse(value: String): TextMagnitude? {
            var text = value.trim()
            if (text.lastOrNull() in listOf('f', 'F', 'd', 'D')) text = text.dropLast(1)
            text = text.removePrefix("+").removePrefix("-")
            val binary = text.startsWith("0x", ignoreCase = true)
            val exponentIndex = text.indexOfFirst { if (binary) it == 'p' || it == 'P' else it == 'e' || it == 'E' }
            if (binary && exponentIndex < 0) return null
            val exponentText = if (exponentIndex < 0) "0" else text.substring(exponentIndex + 1)
            // String length is Int-bounded, so larger exponents cannot be balanced by its mantissa.
            val exponent = exponentText.toLongOrNull()?.coerceIn(-1_000_000_000_000L, 1_000_000_000_000L)
                ?: if (exponentText.startsWith('-')) -1_000_000_000_000L else 1_000_000_000_000L
            val mantissa = (if (exponentIndex < 0) text else text.substring(0, exponentIndex))
                .let { if (binary) it.drop(2) else it }
            val point = mantissa.indexOf('.')
            val fractionLength = if (point < 0) 0 else mantissa.length - point - 1
            val radix = if (binary) 16 else 10
            val rawDigits = mantissa.replace(".", "")
            if (rawDigits.isEmpty() || rawDigits.any { it.digitToIntOrNull(radix) == null }) return null
            val digits = (if (binary) buildString {
                rawDigits.forEach { append(it.digitToInt(16).toString(2).padStart(4, '0')) }
            } else rawDigits).trimStart('0')
            if (digits.isEmpty()) return TextMagnitude("0", Long.MIN_VALUE, binary)
            return TextMagnitude(digits.trimEnd('0'), digits.length.toLong() + exponent - fractionLength.toLong() * if (binary) 4 else 1, binary)
        }
    }
}

private fun String.multiplySmall(factor: Int): String = buildString(length + 1) {
    var carry = 0
    for (index in this@multiplySmall.indices.reversed()) {
        val product = this@multiplySmall[index].digitToInt() * factor + carry
        append(('0'.code + product % 10).toChar())
        carry = product / 10
    }
    if (carry != 0) append(('0'.code + carry).toChar())
    reverse()
}
