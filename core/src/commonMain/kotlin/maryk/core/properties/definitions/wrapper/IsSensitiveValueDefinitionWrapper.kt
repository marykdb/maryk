package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext

/**
 * Wrapper for simple value definitions which can be marked as sensitive.
 */
interface IsSensitiveValueDefinitionWrapper<T : Any, TO : Any, in CX : IsPropertyContext, in DO> :
    IsValueDefinitionWrapper<T, TO, CX, DO> {
    val sensitive: Boolean
}
