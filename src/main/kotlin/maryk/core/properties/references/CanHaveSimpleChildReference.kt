package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/** Reference to properties of type [T] defined by [D] which can have simple children */
abstract class CanHaveSimpleChildReference<T: Any, out D : IsPropertyDefinition<T>, out P: IsPropertyReference<*, *>>(
    definition: D,
    parentReference: P? = null
) : PropertyReference<T, D, P>(definition, parentReference)