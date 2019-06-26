package maryk.core.properties.definitions

/** Interface to define something can be en/decoded to byte array */
interface IsStorageBytesEncodable<T : Any> {
    /** Read stored value from [reader] until [length] */
    fun readStorageBytes(length: Int, reader: () -> Byte): T

    /** Calculates the byte size of the storage bytes for [value] */
    fun calculateStorageByteLength(value: T): Int

    /** Write a [value] to bytes with [writer] */
    fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)

    /** Read storage [bytes] from [offset] until [length] */
    fun fromStorageBytes(bytes: ByteArray, offset: Int, length: Int): T {
        var index = offset
        return this.readStorageBytes(length) {
            bytes[index++]
        }
    }

    /** write value to storage bytes */
    fun toStorageBytes(value: T): ByteArray {
        val bytes = ByteArray(
            this.calculateStorageByteLength(value)
        )
        var index = 0
        this.writeStorageBytes(value) { bytes[index++] = it }
        return bytes
    }
}
