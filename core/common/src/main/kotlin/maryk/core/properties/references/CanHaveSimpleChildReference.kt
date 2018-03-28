package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/**
 * Reference to properties of type [T] defined by [D] which can have simple children
 * Has an optional [parentReference]
 */
abstract class CanHaveSimpleChildReference<
        T: Any,
        out D : IsPropertyDefinition<T>,
        out P: IsPropertyReference<*, *>
> internal constructor(
    definition: D,
    parentReference: P? = null
) : PropertyReference<T, D, P>(definition, parentReference)