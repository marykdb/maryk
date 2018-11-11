package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/**
 * Reference of type [T] to a property defined by [propertyDefinition] by type [D]
 * with parent [parentReference] of type [P]
 */
abstract class PropertyReference<
    T: Any,
    out D : IsPropertyDefinition<T>,
    out P: AnyPropertyReference,
    C: Any
> internal constructor(
    final override val propertyDefinition: D,
    val parentReference: P?
): IsPropertyReference<T, D, C> {
    override fun toString() = this.completeName

    override fun equals(other: Any?) = when {
        this === other -> true
        other == null || other !is AnyPropertyReference -> false
        else -> other.completeName == this.completeName
    }

    override fun hashCode() = this.completeName.hashCode()
}
