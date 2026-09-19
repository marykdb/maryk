package maryk.datastore.foundationdb.processors.helpers

internal class ByteArrayKey private constructor(private val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteArrayKey) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    companion object {
        fun wrap(bytes: ByteArray, copy: Boolean = false): ByteArrayKey {
            val stored = if (copy) bytes.copyOf() else bytes
            return ByteArrayKey(stored)
        }
    }
}

internal fun ByteArray.asByteArrayKey(copy: Boolean = false): ByteArrayKey = ByteArrayKey.wrap(this, copy)
