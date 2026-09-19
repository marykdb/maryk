package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/** A cache-able property reference */
interface IsPropertyReferenceForCache<T : Any, out D : IsPropertyDefinition<T>>  {
    val propertyDefinition: D
}
