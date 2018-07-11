package maryk.core.properties

import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper

abstract class AbstractPropertyDefinitions<DO: Any> : IsPropertyDefinitions {
    /** Add a single property definition wrapper */
    internal abstract fun addSingle(propertyDefinitionWrapper: IsPropertyDefinitionWrapper<out Any, *, *, DO>)
}
