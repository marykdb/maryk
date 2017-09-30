package maryk.core.properties.types.numeric

abstract class NumberDescriptor<T: Comparable<T>>(
        val size: Int
) {
    abstract fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)
    abstract fun fromStorageByteReader(length: Int, reader: () -> Byte): T
    abstract fun ofString(value: String): T
    abstract fun createRandom(): T
    fun writeStorageBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        reserver(size)
        this.writeStorageBytes(value, writer)
    }
}