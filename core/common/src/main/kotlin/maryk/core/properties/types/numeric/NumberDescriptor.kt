package maryk.core.properties.types.numeric

import maryk.core.protobuf.WireType

abstract class NumberDescriptor<T: Comparable<T>> internal constructor(
    internal val size: Int,
    internal val wireType: WireType,
    internal val type: NumberType
) {
    internal abstract fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)
    internal abstract fun fromStorageByteReader(length: Int, reader: () -> Byte): T
    internal abstract fun ofString(value: String): T
    internal abstract fun createRandom(): T
    internal abstract fun calculateTransportByteLength(value: T): Int
    internal abstract fun readTransportBytes(reader: () -> Byte): T
    internal abstract fun writeTransportBytes(value: T, writer: (byte: Byte) -> Unit)
    internal abstract fun ofDouble(value: Double): T
    internal abstract fun ofInt(value: Int): T
    internal abstract fun ofLong(value: Long): T
    internal abstract fun isOfType(value: Any): Boolean
}