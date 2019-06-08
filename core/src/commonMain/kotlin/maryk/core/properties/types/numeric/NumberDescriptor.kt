package maryk.core.properties.types.numeric

import maryk.core.protobuf.WireType

abstract class NumberDescriptor<T : Comparable<T>> internal constructor(
    internal val size: Int,
    internal val wireType: WireType,
    val type: NumberType,
    val zero: T
) {
    /** Sum [value1] with [value2] */
    abstract fun sum(value1: T, value2: T): T

    /** Divide [value1] with [value2] */
    abstract fun divide(value1: T, value2: T): T

    /** Write number in [value] from storage with [writer] */
    internal abstract fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)

    /** Read number with [reader] from storage of [length] */
    internal abstract fun fromStorageByteReader(length: Int, reader: () -> Byte): T

    /** Create number by string [value] */
    internal abstract fun ofString(value: String): T

    /** Create random value */
    internal abstract fun createRandom(): T

    /** Calculate transport bytes length of [value] */
    internal abstract fun calculateTransportByteLength(value: T): Int

    /** Read number from transport bytes [reader]*/
    internal abstract fun readTransportBytes(reader: () -> Byte): T

    /** Write [value] number to transport bytes with [writer] */
    internal abstract fun writeTransportBytes(value: T, writer: (byte: Byte) -> Unit)

    /** Create number from [double] */
    internal abstract fun ofDouble(double: Double): T

    /** Create number from [int] */
    internal abstract fun ofInt(int: Int): T

    /** Create number from [long] */
    internal abstract fun ofLong(long: Long): T

    /** Check if given [value] is of type described */
    internal abstract fun isOfType(value: Any): Boolean

    /** Create number in [value] to a double */
    internal abstract fun toDouble(value: T): Double
}
