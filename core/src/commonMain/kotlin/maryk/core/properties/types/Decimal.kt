package maryk.core.properties.types

import kotlin.experimental.inv

/** Exact fixed-point decimal with an arbitrary-size signed unscaled integer. */
class Decimal private constructor(
    private val integer: SignedInteger,
    val scale: UInt,
) : Comparable<Decimal> {
    init {
        require(scale <= MAX_SCALE) { "Decimal scale must be between 0 and $MAX_SCALE" }
    }

    /** Compatibility accessor for callers whose value fits in a signed [Long]. */
    val unscaledValue: Long get() = integer.toLongExact()

    operator fun plus(other: Decimal): Decimal {
        val resultScale = maxOf(scale, other.scale)
        return Decimal(
            integer.multiplyPowerOfTen((resultScale - scale).toInt()) +
                other.integer.multiplyPowerOfTen((resultScale - other.scale).toInt()),
            resultScale,
        )
    }

    operator fun minus(other: Decimal): Decimal = this + -other

    operator fun unaryMinus(): Decimal = Decimal(-integer, scale)

    operator fun times(other: Decimal): Decimal {
        val resultScale = scale + other.scale
        require(resultScale <= MAX_SCALE) { "Decimal multiplication scale exceeds $MAX_SCALE" }
        return Decimal(integer * other.integer, resultScale)
    }

    operator fun div(other: Decimal): Decimal = divideExact(other)

    fun divideExact(other: Decimal, resultScale: UInt = scale): Decimal {
        require(other.integer.isNotZero()) { "Cannot divide by zero" }
        require(resultScale <= MAX_SCALE) { "Decimal scale must be between 0 and $MAX_SCALE" }
        val exponent = resultScale.toInt() + other.scale.toInt() - scale.toInt()
        val numerator = if (exponent >= 0) integer.multiplyPowerOfTen(exponent) else integer
        val denominator = if (exponent >= 0) other.integer else other.integer.multiplyPowerOfTen(-exponent)
        val (quotient, remainder) = numerator.divideAndRemainder(denominator)
        if (remainder.isNotZero()) throw ArithmeticException("Decimal division would lose precision")
        return Decimal(quotient, resultScale)
    }

    fun rescaleExact(newScale: UInt): Decimal {
        require(newScale <= MAX_SCALE) { "Decimal scale must be between 0 and $MAX_SCALE" }
        if (newScale == scale) return this
        val difference = newScale.toInt() - scale.toInt()
        return if (difference > 0) {
            Decimal(integer.multiplyPowerOfTen(difference), newScale)
        } else {
            val (quotient, remainder) = integer.divideAndRemainder(SignedInteger.powerOfTen(-difference))
            if (remainder.isNotZero()) throw ArithmeticException("Decimal rescale would lose precision")
            Decimal(quotient, newScale)
        }
    }

    override fun compareTo(other: Decimal): Int {
        val resultScale = maxOf(scale, other.scale)
        return integer.multiplyPowerOfTen((resultScale - scale).toInt()).compareTo(
            other.integer.multiplyPowerOfTen((resultScale - other.scale).toInt())
        )
    }

    override fun toString(): String {
        val digits = integer.magnitude.toDecimalString()
        val prefix = if (integer.negative) "-" else ""
        if (scale == 0u) return prefix + digits
        val padded = digits.padStart(scale.toInt() + 1, '0')
        val pointIndex = padded.length - scale.toInt()
        return "$prefix${padded.substring(0, pointIndex)}.${padded.substring(pointIndex)}"
    }

    override fun equals(other: Any?): Boolean =
        other is Decimal && integer == other.integer && scale == other.scale

    override fun hashCode(): Int = 31 * integer.hashCode() + scale.hashCode()

    internal fun fitsInSignedBytes(byteSize: Int) = integer.fitsInSignedBytes(byteSize)

    internal fun toSignedBytes(byteSize: Int) = integer.toSignedBytes(byteSize)

    internal fun divideAndRemainder(count: Long): Pair<Decimal, Decimal> {
        require(count > 0L) { "Divisor must be positive" }
        val (quotient, remainder) = integer.divideAndRemainder(SignedInteger.fromLong(count))
        return Decimal(quotient, scale) to Decimal(remainder, scale)
    }

    internal fun isOdd() = integer.magnitude.isOdd()

    internal fun absoluteTwiceCompareTo(value: Long): Int = integer.magnitude.timesTwo().compareTo(UnsignedInteger.fromLong(value))

    internal fun roundedAwayFromZero(): Decimal = Decimal(integer.addOneInSignDirection(), scale)

    companion object {
        const val MAX_SCALE = 18u

        fun fromUnscaled(unscaledValue: Long, scale: UInt): Decimal = Decimal(SignedInteger.fromLong(unscaledValue), scale)

        /** Creates a decimal from an arbitrary-size signed integer and an explicit fixed-point [scale]. */
        fun fromUnscaled(unscaledValue: String, scale: UInt): Decimal {
            require(integerPattern.matches(unscaledValue)) { "Invalid unscaled decimal value `$unscaledValue`" }
            val magnitude = UnsignedInteger.parseDecimal(unscaledValue.removePrefix("+").removePrefix("-"))
            return Decimal(SignedInteger(unscaledValue.startsWith('-') && !magnitude.isZero, magnitude), scale)
        }

        fun parse(value: String): Decimal {
            require(decimalPattern.matches(value)) { "Invalid decimal value `$value`" }
            val unsigned = value.removePrefix("+").removePrefix("-")
            val separator = unsigned.indexOf('.')
            val scale = if (separator < 0) 0u else (unsigned.length - separator - 1).toUInt()
            require(scale <= MAX_SCALE) { "Decimal scale must be between 0 and $MAX_SCALE" }
            val magnitude = UnsignedInteger.parseDecimal(unsigned.replace(".", ""))
            return Decimal(SignedInteger(value.startsWith('-') && !magnitude.isZero, magnitude), scale)
        }

        internal fun fromSignedBytes(bytes: ByteArray, scale: UInt): Decimal =
            Decimal(SignedInteger.fromSignedBytes(bytes), scale)

        internal fun maximumForSignedBytes(byteSize: Int, scale: UInt): Decimal =
            Decimal(SignedInteger.maximumForSignedBytes(byteSize), scale)

        private val integerPattern = Regex("[+-]?\\d+")
        private val decimalPattern = Regex("[+-]?\\d+(?:\\.\\d+)?")
    }
}

/** Creates an exact decimal integer value, optionally preserving [scale] trailing zeros. */
fun Byte.toDecimal(scale: UInt = 0u): Decimal = toLong().toDecimal(scale)

/** Creates an exact decimal integer value, optionally preserving [scale] trailing zeros. */
fun Short.toDecimal(scale: UInt = 0u): Decimal = toLong().toDecimal(scale)

/** Creates an exact decimal integer value, optionally preserving [scale] trailing zeros. */
fun Int.toDecimal(scale: UInt = 0u): Decimal = toLong().toDecimal(scale)

/** Creates an exact decimal integer value, optionally preserving [scale] trailing zeros. */
fun Long.toDecimal(scale: UInt = 0u): Decimal = Decimal.fromUnscaled(this, 0u).rescaleExact(scale)

/** Creates an exact decimal integer value, optionally preserving [scale] trailing zeros. */
fun UByte.toDecimal(scale: UInt = 0u): Decimal = toULong().toDecimal(scale)

/** Creates an exact decimal integer value, optionally preserving [scale] trailing zeros. */
fun UShort.toDecimal(scale: UInt = 0u): Decimal = toULong().toDecimal(scale)

/** Creates an exact decimal integer value, optionally preserving [scale] trailing zeros. */
fun UInt.toDecimal(scale: UInt = 0u): Decimal = toULong().toDecimal(scale)

/** Creates an exact decimal integer value, optionally preserving [scale] trailing zeros. */
fun ULong.toDecimal(scale: UInt = 0u): Decimal = Decimal.fromUnscaled(toString(), 0u).rescaleExact(scale)

private class UnsignedInteger private constructor(private val bytes: ByteArray) : Comparable<UnsignedInteger> {
    val isZero get() = bytes.isEmpty()

    override fun compareTo(other: UnsignedInteger): Int {
        if (bytes.size != other.bytes.size) return bytes.size.compareTo(other.bytes.size)
        bytes.indices.forEach { index ->
            val comparison = bytes[index].toUByte().compareTo(other.bytes[index].toUByte())
            if (comparison != 0) return comparison
        }
        return 0
    }

    operator fun plus(other: UnsignedInteger): UnsignedInteger {
        val result = ByteArray(maxOf(bytes.size, other.bytes.size) + 1)
        var carry = 0
        for (offset in 0 until result.size) {
            val left = bytes.getOrNull(bytes.size - 1 - offset)?.toUByte()?.toInt() ?: 0
            val right = other.bytes.getOrNull(other.bytes.size - 1 - offset)?.toUByte()?.toInt() ?: 0
            val sum = left + right + carry
            result[result.lastIndex - offset] = sum.toByte()
            carry = sum ushr 8
        }
        return ofBytes(result)
    }

    fun subtract(other: UnsignedInteger): UnsignedInteger {
        require(this >= other) { "Unsigned subtraction underflow" }
        val result = bytes.copyOf()
        var borrow = 0
        for (offset in bytes.indices) {
            val index = result.lastIndex - offset
            var difference = result[index].toUByte().toInt() -
                (other.bytes.getOrNull(other.bytes.lastIndex - offset)?.toUByte()?.toInt() ?: 0) - borrow
            if (difference < 0) {
                difference += 256
                borrow = 1
            } else borrow = 0
            result[index] = difference.toByte()
        }
        return ofBytes(result)
    }

    operator fun times(other: UnsignedInteger): UnsignedInteger {
        if (isZero || other.isZero) return ZERO
        val result = IntArray(bytes.size + other.bytes.size)
        for (leftIndex in bytes.indices.reversed()) {
            for (rightIndex in other.bytes.indices.reversed()) {
                val target = leftIndex + rightIndex + 1
                result[target] += bytes[leftIndex].toUByte().toInt() * other.bytes[rightIndex].toUByte().toInt()
            }
        }
        for (index in result.indices.reversed()) {
            if (index > 0) result[index - 1] += result[index] ushr 8
            result[index] = result[index] and 0xff
        }
        return ofBytes(ByteArray(result.size) { result[it].toByte() })
    }

    fun multiplySmall(value: Int): UnsignedInteger {
        if (isZero || value == 0) return ZERO
        val result = ByteArray(bytes.size + 4)
        var carry = 0
        for (offset in bytes.indices) {
            val product = bytes[bytes.lastIndex - offset].toUByte().toInt() * value + carry
            result[result.lastIndex - offset] = product.toByte()
            carry = product ushr 8
        }
        var index = result.size - bytes.size - 1
        while (carry != 0) {
            result[index--] = carry.toByte()
            carry = carry ushr 8
        }
        return ofBytes(result)
    }

    fun divideAndRemainder(divisor: UnsignedInteger): Pair<UnsignedInteger, UnsignedInteger> {
        require(!divisor.isZero) { "Cannot divide by zero" }
        if (this < divisor) return ZERO to this
        var quotient = ZERO
        var remainder = ZERO
        for (bit in bitLength - 1 downTo 0) {
            quotient = quotient.shiftLeftOne()
            remainder = remainder.shiftLeftOne()
            if (bitAt(bit)) remainder += ONE
            if (remainder >= divisor) {
                remainder = remainder.subtract(divisor)
                quotient += ONE
            }
        }
        return quotient to remainder
    }

    fun toDecimalString(): String {
        if (isZero) return "0"
        var value = this
        val groups = mutableListOf<Int>()
        val divisor = fromLong(1_000_000_000L)
        while (!value.isZero) {
            val (quotient, remainder) = value.divideAndRemainder(divisor)
            groups += remainder.toLongExact().toInt()
            value = quotient
        }
        return buildString {
            append(groups.last())
            for (index in groups.lastIndex - 1 downTo 0) append(groups[index].toString().padStart(9, '0'))
        }
    }

    fun toLongExact(): Long {
        require(bytes.size <= 8) { "Value does not fit in Long" }
        var result = 0L
        bytes.forEach { result = (result shl 8) or it.toUByte().toLong() }
        require(result >= 0L) { "Value does not fit in Long" }
        return result
    }

    fun toByteArray() = bytes.copyOf()

    fun timesTwo() = multiplySmall(2)

    fun isOdd() = !isZero && (bytes.last().toInt() and 1) != 0

    private fun shiftLeftOne() = multiplySmall(2)

    private fun bitAt(bit: Int): Boolean {
        val byteOffset = bytes.size - 1 - bit / 8
        return byteOffset >= 0 && (bytes[byteOffset].toInt() and (1 shl (bit % 8))) != 0
    }

    private val bitLength: Int get() {
        if (isZero) return 0
        val leading = bytes.first().toUByte().toInt()
        return (bytes.size - 1) * 8 + (32 - leading.countLeadingZeroBits())
    }

    override fun equals(other: Any?) = other is UnsignedInteger && bytes.contentEquals(other.bytes)
    override fun hashCode() = bytes.contentHashCode()

    companion object {
        val ZERO = UnsignedInteger(ByteArray(0))
        val ONE = UnsignedInteger(byteArrayOf(1))

        fun ofBytes(bytes: ByteArray): UnsignedInteger {
            val first = bytes.indexOfFirst { it != 0.toByte() }
            return if (first < 0) ZERO else UnsignedInteger(bytes.copyOfRange(first, bytes.size))
        }

        fun fromLong(value: Long): UnsignedInteger {
            require(value >= 0) { "Unsigned value cannot be negative" }
            return parseDecimal(value.toString())
        }

        fun parseDecimal(value: String): UnsignedInteger {
            var result = ZERO
            value.forEach { result = result.multiplySmall(10) + UnsignedInteger(byteArrayOf((it - '0').toByte())) }
            return result
        }
    }
}

private class SignedInteger(val negative: Boolean, val magnitude: UnsignedInteger) : Comparable<SignedInteger> {
    init { require(!negative || !magnitude.isZero) }

    operator fun plus(other: SignedInteger): SignedInteger = when {
        negative == other.negative -> SignedInteger(negative, magnitude + other.magnitude)
        magnitude >= other.magnitude -> of(negative, magnitude.subtract(other.magnitude))
        else -> of(other.negative, other.magnitude.subtract(magnitude))
    }

    operator fun unaryMinus() = of(!negative, magnitude)
    operator fun times(other: SignedInteger) = of(negative != other.negative, magnitude * other.magnitude)

    override fun compareTo(other: SignedInteger): Int {
        if (negative != other.negative) return if (negative) -1 else 1
        val comparison = magnitude.compareTo(other.magnitude)
        return if (negative) -comparison else comparison
    }

    fun multiplyPowerOfTen(power: Int): SignedInteger {
        var result = magnitude
        repeat(power) { result = result.multiplySmall(10) }
        return of(negative, result)
    }

    fun divideAndRemainder(divisor: SignedInteger): Pair<SignedInteger, SignedInteger> {
        require(!divisor.magnitude.isZero) { "Cannot divide by zero" }
        val (quotient, remainder) = magnitude.divideAndRemainder(divisor.magnitude)
        return of(negative != divisor.negative, quotient) to of(negative, remainder)
    }

    fun isNotZero() = !magnitude.isZero
    fun addOneInSignDirection() = of(negative, magnitude + UnsignedInteger.ONE)

    fun toLongExact(): Long {
        val value = magnitude.toDecimalString()
        if (!negative) return value.toLong()
        if (value == "9223372036854775808") return Long.MIN_VALUE
        return -value.toLong()
    }

    fun fitsInSignedBytes(byteSize: Int): Boolean = try {
        toSignedBytes(byteSize)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    fun toSignedBytes(byteSize: Int): ByteArray {
        require(byteSize > 0) { "Byte size must be positive" }
        val result = ByteArray(byteSize)
        val magnitudeBytes = magnitude.toByteArray()
        require(magnitudeBytes.size <= byteSize) { "Value does not fit in $byteSize bytes" }
        magnitudeBytes.copyInto(result, byteSize - magnitudeBytes.size)
        if (!negative) {
            require(result.first().toInt() >= 0) { "Value does not fit in $byteSize bytes" }
            return result
        }
        result.indices.forEach { result[it] = result[it].inv() }
        var carry = 1
        for (index in result.indices.reversed()) {
            val sum = result[index].toUByte().toInt() + carry
            result[index] = sum.toByte()
            carry = sum ushr 8
        }
        require(result.first().toInt() < 0) { "Value does not fit in $byteSize bytes" }
        return result
    }

    override fun equals(other: Any?) = other is SignedInteger && negative == other.negative && magnitude == other.magnitude
    override fun hashCode() = 31 * negative.hashCode() + magnitude.hashCode()

    companion object {
        fun of(negative: Boolean, magnitude: UnsignedInteger) =
            if (magnitude.isZero) SignedInteger(false, magnitude) else SignedInteger(negative, magnitude)

        fun fromLong(value: Long): SignedInteger =
            if (value < 0) SignedInteger(true, UnsignedInteger.parseDecimal(value.toString().removePrefix("-")))
            else SignedInteger(false, UnsignedInteger.fromLong(value))

        fun powerOfTen(power: Int) = SignedInteger(false, UnsignedInteger.parseDecimal("1" + "0".repeat(power)))

        fun fromSignedBytes(bytes: ByteArray): SignedInteger {
            require(bytes.isNotEmpty()) { "Signed bytes cannot be empty" }
            val negative = bytes.first().toInt() < 0
            val magnitude = bytes.copyOf()
            if (negative) {
                magnitude.indices.forEach { magnitude[it] = magnitude[it].inv() }
                var carry = 1
                for (index in magnitude.indices.reversed()) {
                    val sum = magnitude[index].toUByte().toInt() + carry
                    magnitude[index] = sum.toByte()
                    carry = sum ushr 8
                }
            }
            return of(negative, UnsignedInteger.ofBytes(magnitude))
        }

        fun maximumForSignedBytes(byteSize: Int): SignedInteger {
            require(byteSize > 0) { "Byte size must be positive" }
            val bytes = ByteArray(byteSize) { 0xff.toByte() }
            bytes[0] = 0x7f
            return fromSignedBytes(bytes)
        }
    }
}
