package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/**
 * Reference to properties of type [T] defined by [D] which can have simple children
 * Has an optional [parentReference]
 */
abstract class CanHaveComplexChildReference<
    T: Any,
    out D : IsPropertyDefinition<T>,
    out P: AnyPropertyReference,
    C: Any
> internal constructor(
    definition: D,
    parentReference: P?
) : CanHaveSimpleChildReference<T, D, P, C>(definition, parentReference)
