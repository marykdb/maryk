package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/**
 * Interface for properties which can have complex children
 * @param <T> Type contained within Property
 * @param <D> Type of property definition
 */
abstract class CanHaveSimpleChildReference<T: Any, out D : IsPropertyDefinition<T>, out P: IsPropertyReference<*, *>>(
        definition: D,
        parentReference: P? = null
) : PropertyReference<T, D, P>(definition, parentReference)
