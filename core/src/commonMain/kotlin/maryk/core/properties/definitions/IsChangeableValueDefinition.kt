package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/**
 * Property Definition to define properties containing changeable values of [T].
 * This so the references can be generically used in changes.
 * Can be both DefinitionWrappers or Definition
 */
interface IsChangeableValueDefinition<T : Any, in CX : IsPropertyContext> : IsSerializablePropertyDefinition<T, CX>
