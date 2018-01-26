package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/**
 * Reference to properties of type [T] defined by [D] which can have simple children
 * Has an optional [parentReference]
 */
abstract class CanHaveComplexChildReference<T: Any, out D : IsPropertyDefinition<T>, out P: IsPropertyReference<*, *>>(
    definition: D,
    parentReference: P? = null
) : CanHaveSimpleChildReference<T, D, P>(definition, parentReference)