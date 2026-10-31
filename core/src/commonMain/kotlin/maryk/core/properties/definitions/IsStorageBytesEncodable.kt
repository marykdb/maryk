package maryk.core.properties.definitions

/** Interface to define something can be en/decoded to byte array */
interface IsStorageBytesEncodable<T : Any> {
    /** Read stored value from [reader] until [length] */
    fun readStorageBytes(length: Int, reader: () -> Byte): T

    /** Calculates the byte size of the storage bytes for [value] */
    fun calculateStorageByteLength(value: T): Int

    /** Write a [value] to bytes with [writer] */
    fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)

    /** Read [length] storage bytes from [offset], defaulting to the remaining array tail. */
    fun fromStorageBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): T {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length) {
            "Storage byte slice is outside the array"
        }
        var index = offset
        return this.readStorageBytes(length) {
            require(index - offset < length) { "Storage value exceeds its byte slice" }
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
