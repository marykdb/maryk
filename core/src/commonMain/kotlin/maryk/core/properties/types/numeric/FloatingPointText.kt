package maryk.core.properties.types.numeric

/** Text values preserve NaN payloads; collection reference names keep their canonical spelling. */
internal fun <T : Comparable<T>> NumberDescriptor<T>.asTransportString(value: T): String = when (this) {
    Float32 -> (value as Float).let {
        val bits = it.toRawBits()
        if (it.isNaN() && bits != Float.NaN.toRawBits()) {
            "NaN:0x${bits.toUInt().toString(16).padStart(8, '0')}"
        } else asCanonicalString(value)
    }
    Float64 -> (value as Double).let {
        val bits = it.toRawBits()
        if (it.isNaN() && bits != Double.NaN.toRawBits()) {
            "NaN:0x${bits.toULong().toString(16).padStart(16, '0')}"
        } else asCanonicalString(value)
    }
    else -> value.toString()
}

/** Keep signed zero distinct even on JavaScript, whose Number.toString() returns "0". */
internal fun <T : Comparable<T>> NumberDescriptor<T>.asCanonicalString(value: T): String = when (this) {
    Float32 -> if ((value as Float).toRawBits() == Int.MIN_VALUE) "-0.0" else value.toString()
    Float64 -> if ((value as Double).toRawBits() == Long.MIN_VALUE) "-0.0" else value.toString()
    else -> value.toString()
}

internal fun parseFloat32Text(value: String): Float {
    if (!value.startsWith("NaN:")) return value.toFloat()
    val bits = rawNanHex(value, 8).toUInt(16).toInt()
    require(bits and 0x7f800000 == 0x7f800000 && bits and 0x007fffff != 0) {
        "Raw Float32 NaN text must contain NaN bits"
    }
    return Float.fromBits(bits)
}

internal fun parseFloat64Text(value: String): Double {
    if (!value.startsWith("NaN:")) return value.toDouble()
    val bits = rawNanHex(value, 16).toULong(16).toLong()
    require(bits and 0x7ff0000000000000L == 0x7ff0000000000000L && bits and 0x000fffffffffffffL != 0L) {
        "Raw Float64 NaN text must contain NaN bits"
    }
    return Double.fromBits(bits)
}

private fun rawNanHex(value: String, digits: Int): String {
    require(value.startsWith("NaN:0x") && value.length == 6 + digits) {
        "Raw NaN text must contain exactly $digits hexadecimal digits"
    }
    return value.substring(6).also { hex ->
        require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "Raw NaN text contains invalid hexadecimal digits"
        }
    }
}
