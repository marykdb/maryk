package maryk.core.properties

import maryk.core.models.BaseDataModel
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.properties.definitions.wrapper.AnyTypedDefinitionWrapper

/**
 * Mutable variant of DataModel for a IsCollectionDefinition implementation
 * which can be used to construct a DataModel from a serialized form.
 * It is also used to pass on to context capturers, so they can get the properties while
 * a DataModel is being constructed from a serialized form.
 */
class MutablePropertiesCollection<DO: Any> : BaseDataModel<DO>(), MutableCollection<AnyTypedDefinitionWrapper<DO>> {
    override val Serializer: IsDataModelSerializer<*, *, *>
        get() = throw NotImplementedError("Should never be called as this is only a properties container")

    override fun add(element: AnyTypedDefinitionWrapper<DO>): Boolean {
        this.addSingle(propertyDefinitionWrapper = element)
        return true
    }

    override fun addAll(elements: Collection<AnyTypedDefinitionWrapper<DO>>): Boolean {
        elements.forEach {
            this.addSingle(it)
        }
        return true
    }

    override fun clear() {}
    override fun remove(element: AnyTypedDefinitionWrapper<DO>) = false
    override fun removeAll(elements: Collection<AnyTypedDefinitionWrapper<DO>>) = false
    override fun retainAll(elements: Collection<AnyTypedDefinitionWrapper<DO>>) = false
}
