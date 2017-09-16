package maryk.core.properties.types.numeric

abstract class NumberDescriptor<T: Comparable<T>>(
        val size: Int
) {
    abstract fun ofBytes(bytes: ByteArray, offset: Int = 0, length: Int = size): T
    abstract fun toBytes(value: T, bytes: ByteArray?, offset: Int): ByteArray
    abstract fun ofString(value: String): T
    abstract fun createRandom(): T
    abstract fun fromByteReader(length: Int, reader: () -> Byte): T
    abstract fun writeBytes(value: T, writer: (byte: Byte) -> Unit)
    fun writeBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        reserver(size)
        this.writeBytes(value, writer)
    }
}