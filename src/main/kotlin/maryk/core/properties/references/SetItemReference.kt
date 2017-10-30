package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.SetDefinition

/** Reference to a Set Item by value
 * @param value           index of property reference
 * @param parentReference reference to parent
 * @param <V> value type
 */
class SetItemReference<T: Any>(
        val value: T,
        parentReference: PropertyReference<Set<T>, SetDefinition<T, *>>
) : CanHaveSimpleChildReference<T, IsPropertyDefinition<T>>(
        parentReference.propertyDefinition.valueDefinition, parentReference
), EmbeddedPropertyReference<T> {
    override val name: String? get() = parentReference?.name

    override val completeName: String get() = "${this.parentReference!!.completeName}[${value}]"
}