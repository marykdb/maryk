package maryk.core.properties.references

import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.ListDefinition

/**
 * Reference to a List Item by index
 *
 * @param index           index of property reference
 * @param parentReference reference to parent
 * @param <T> value type
 */
class ListItemReference<T: Any> (
        val index: Int,
        parentReference: PropertyReference<Array<T>, ListDefinition<T>>
) : CanHaveSimpleChildReference<T, AbstractValueDefinition<T>>(
        parentReference.propertyDefinition.valueDefinition, parentReference
), EmbeddedPropertyReference<T> {
    override val name = parentReference.name

    override val completeName: String get() = "${this.parentReference!!.completeName}[$index]"
}