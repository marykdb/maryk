package maryk.core.properties.references

import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.ListDefinition

/** Reference to a List Item by index
 * @param index           index of property reference
 * @param parentReference reference to parent
 * @param <T> value type
 */
class ListItemReference<T: Any> (
        val index: Int,
        parentReference: PropertyReference<List<T>, ListDefinition<T, *>>
) : CanHaveSimpleChildReference<T, AbstractValueDefinition<T, *>>(
        parentReference.propertyDefinition.valueDefinition, parentReference
), EmbeddedPropertyReference<T> {
    override val name = parentReference.name

    override val completeName: String get() = "${this.parentReference!!.completeName}.#$index"

    override fun calculateTransportByteLength(): Int {
        val parentLength = parentReference?.calculateTransportByteLength() ?: 0
        return parentLength + 1
    }

    override fun writeTransportBytes(writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(writer)
        writer(1)
    }
}