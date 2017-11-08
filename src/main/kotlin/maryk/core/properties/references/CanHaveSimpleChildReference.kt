package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/**
 * Interface for properties which can have complex children
 * @param <T> Type contained within Property
 * @param <D> Type of property definition
 */
open class CanHaveSimpleChildReference<T: Any, out D : IsPropertyDefinition<T>>(
        definition: D,
        parentReference: PropertyReference<*, *>? = null
) : PropertyReference<T, D>(definition, parentReference)
