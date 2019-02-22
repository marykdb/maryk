package maryk.core.properties.references

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.IsBytesEncodable
import maryk.core.properties.definitions.key.IsIndexable
import maryk.core.values.Values

interface IsIndexablePropertyReference<T: Any>: IsIndexable, IsBytesEncodable<T> {
    /**
     * Get the value from [values] from dataModel of type [DM]
     * to be used in a fixed bytes encodable
     */
    fun <DM: IsValuesDataModel<*>> getValue(values: Values<DM, *>): T

    /**
     * Check if value getter is defined for property referred by [propertyReference]
     * Useful to resolve filters into key filters which are fixed bytes.
     */
    fun isForPropertyReference(propertyReference: AnyPropertyReference): Boolean

    override fun calculateStorageByteLength(values: Values<*, *>): Int {
        val value = this.getValue(values)
        return this.calculateStorageByteLength(value)
    }

    override fun writeStorageBytes(values: Values<*, *>, writer: (byte: Byte) -> Unit) {
        val value = this.getValue(values)
        this.writeStorageBytes(value, writer)
    }
}
