package maryk.core.properties.types.numeric

abstract class NumberDescriptor<T: Comparable<T>>(
        val size: Int
) {
    abstract fun writeBytes(value: T, writer: (byte: Byte) -> Unit)
    abstract fun fromByteReader(length: Int, reader: () -> Byte): T
    abstract fun ofString(value: String): T
    abstract fun createRandom(): T
    fun writeBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        reserver(size)
        this.writeBytes(value, writer)
    }
}