package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/**
 * Reference to properties which can have children
 * @param definition definition of property
 * @param parentReference parent to reference to
 * @param T Type contained within Property
 * @param D Type of property definition
 */
abstract class CanHaveComplexChildReference<T: Any, out D : IsPropertyDefinition<T>, out P: IsPropertyReference<*, *>>(
    definition: D,
    parentReference: P? = null
) : CanHaveSimpleChildReference<T, D, P>(definition, parentReference)