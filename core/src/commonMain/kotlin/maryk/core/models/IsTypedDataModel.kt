package maryk.core.models

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.AnyTypedDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper

/**
 * Interface for DataModels which work with objects of type [DO].
 */
interface IsTypedDataModel<DO: Any> :
    IsDataModel,
    Collection<AnyTypedDefinitionWrapper<DO>> {

    override operator fun get(name: String): IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>?
    override operator fun get(index: UInt): IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>?

    val allWithDefaults: List<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>

    fun addSingle(propertyDefinitionWrapper: IsDefinitionWrapper<out Any, *, *, DO>)
}
