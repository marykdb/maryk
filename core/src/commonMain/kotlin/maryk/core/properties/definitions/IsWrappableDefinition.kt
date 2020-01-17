package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper

/** Defines a wrappable definition which has a method to wrap it with a DefinitionWrapper */
interface IsWrappableDefinition<T: Any, CX: IsPropertyContext, W: IsDefinitionWrapper<T, T, CX, *>> {
    fun wrap(index: UInt, name: String, alternativeNames: Set<String>? = null) : W
}
