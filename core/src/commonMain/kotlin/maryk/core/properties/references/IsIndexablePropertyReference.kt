package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.values.IsValuesGetter

interface IsIndexablePropertyReference<T : Any> : IsIndexable, IsStorageBytesEncodable<T> {
    /**
     * Get the value from [values]
     * to be used in a fixed bytes encodable
     */
    fun getValue(values: IsValuesGetter): T

    /**
     * Check if value getter is defined for property referred by [propertyReference]
     * Useful to resolve filters into key filters which are fixed bytes.
     */
    fun isForPropertyReference(propertyReference: AnyPropertyReference): Boolean

    override fun calculateStorageByteLengthForIndex(values: IsValuesGetter, keySize: Int?): Int {
        val value = this.getValue(values)
        val length = this.calculateStorageByteLength(value)
        return length + length.calculateVarByteLength() + (keySize ?: 0)
    }

    override fun writeStorageBytesForIndex(values: IsValuesGetter, key: ByteArray?, writer: (byte: Byte) -> Unit) {
        val value = this.getValue(values)
        this.writeStorageBytes(value, writer)
        this.calculateStorageByteLength(value).writeVarBytes(writer) // write value length
        key?.forEach(writer) // write key to end
    }

    override fun writeStorageBytes(values: IsValuesGetter, writer: (byte: Byte) -> Unit) {
        val value = this.getValue(values)
        this.writeStorageBytes(value, writer)
    }

    fun toQualifierStorageByteArray(): ByteArray?
}
