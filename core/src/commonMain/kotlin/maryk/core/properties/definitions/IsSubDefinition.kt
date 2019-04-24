package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/**
 * Abstract Property Definition containing properties of [T] with context [CX].
 * This is used for simple single value properties and not for lists and maps.
 */
interface IsSubDefinition<T : Any, in CX : IsPropertyContext> : IsChangeableValueDefinition<T, CX>
