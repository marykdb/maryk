package maryk.core.properties.definitions

/** Interface to define something can be en/decoded to byte array */
interface IsBytesEncodable<T : Any> {
    /** Read stored value from [reader] until [length] */
    fun readStorageBytes(length: Int, reader: () -> Byte): T

    /** Calculates the byte size of the storage bytes for [value] */
    fun calculateStorageByteLength(value: T): Int

    /** Write a [value] to bytes with [writer] */
    fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)
}
