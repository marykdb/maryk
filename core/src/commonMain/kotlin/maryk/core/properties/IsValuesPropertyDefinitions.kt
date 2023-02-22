package maryk.core.properties

import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper

interface IsObjectPropertyDefinitions<DO>:
    IsPropertyDefinitions,
    Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>> {
    val allWithDefaults: List<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>

    override operator fun get(name: String): IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>?
    override operator fun get(index: UInt): IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>?

    fun addSingle(propertyDefinitionWrapper: IsDefinitionWrapper<out Any, *, *, DO>)
}

interface IsValuesPropertyDefinitions: IsObjectPropertyDefinitions<Any>
