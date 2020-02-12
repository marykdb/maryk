package maryk.core.properties.definitions

/** Interface to define something can be en/decoded to fixed byte array */
interface IsFixedStorageBytesEncodable<T : Any> : IsStorageBytesEncodable<T> {
    /** The byte size */
    val byteSize: Int

    override fun calculateStorageByteLength(value: T) = byteSize
}
