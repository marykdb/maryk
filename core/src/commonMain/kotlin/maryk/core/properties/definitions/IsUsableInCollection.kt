package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/**
 * Abstract Property Definition containing properties of [T] with context [CX].
 * This is used to signify a value can be used as a collection (List or Set) value
 */
interface IsUsableInCollection<T: Any, in CX: IsPropertyContext> : IsValueDefinition<T, CX>
