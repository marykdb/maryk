package maryk.core.properties.types.numeric

import maryk.core.protobuf.WireType

abstract class NumberDescriptor<T: Comparable<T>>(
        val size: Int,
        val wireType: WireType,
        val type: NumberType
) {
    abstract fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)
    abstract fun fromStorageByteReader(length: Int, reader: () -> Byte): T
    abstract fun ofString(value: String): T
    abstract fun createRandom(): T
    abstract fun calculateTransportByteLength(value: T): Int
    abstract fun readTransportBytes(reader: () -> Byte): T
    abstract fun writeTransportBytes(value: T, writer: (byte: Byte) -> Unit)
}