package maryk.core.values

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference

/** A Values object to get specific values by reference */
interface IsValuesGetter {
    /** Get value of [T] by [propertyReference] */
    operator fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T?
}
