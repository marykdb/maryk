package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/**
 * Abstract Property Definition containing properties of [T] with context [CX].
 * This is used to signify a value can be used as a Map value
 */
interface IsUsableInMapValue<T: Any, in CX: IsPropertyContext> : IsSubDefinition<T, CX>
