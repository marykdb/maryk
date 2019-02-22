package maryk.core.properties.references

import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.values.Values

interface IsFixedBytesPropertyReference<T: Any>: IsFixedBytesEncodable<T>, IsIndexablePropertyReference<T> {
    override fun writeStorageBytes(values: Values<*, *>, writer: (byte: Byte) -> Unit) {
        val value = this.getValue(values)
        this.writeStorageBytes(value, writer)
    }
}
