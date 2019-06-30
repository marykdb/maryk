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
    fun toStorageBytes(value: T, vararg prependWith: Byte): ByteArray {
        val bytes = ByteArray(
            prependWith.size + this.calculateStorageByteLength(value)
        )
        var index = 0

        val writer: (Byte) -> Unit = { bytes[index++] = it }

        for (prependByte in prependWith) {
            writer(prependByte)
        }

        this.writeStorageBytes(value, writer)
        return bytes
    }
}
