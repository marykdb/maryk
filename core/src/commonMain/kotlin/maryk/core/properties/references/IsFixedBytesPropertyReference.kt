package maryk.core.properties.references

import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.IsFixedBytesValueGetter
import maryk.core.properties.definitions.key.IsIndexable
import maryk.core.values.Values

interface IsFixedBytesPropertyReference<T: Any>: IsFixedBytesValueGetter<T>, IsIndexable, IsFixedBytesEncodable<T> {
    val propertyDefinition: IsFixedBytesEncodable<T>

    override fun writeStorageBytes(values: Values<*, *>, writer: (byte: Byte) -> Unit) {
        val value = this.getValue(values)
        this.writeStorageBytes(value, writer)
    }
}
