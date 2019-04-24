package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.wrapper.AbstractPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.graph.PropRefGraphType.PropRef

/**
 * Contains a Flex bytes property [definition] of type [T] which cannot be used in keys or ValueObjects
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class FlexBytesPropertyDefinitionWrapper<T : Any, TO : Any, CX : IsPropertyContext, D : IsSerializableFlexBytesEncodable<T, CX>, DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: D,
    override val getter: (DO) -> TO? = { null },
    override val capturer: ((CX, T) -> Unit)? = null,
    override val toSerializable: ((TO?, CX?) -> T?)? = null,
    override val fromSerializable: ((T?) -> TO?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractPropertyDefinitionWrapper(index, name),
    IsSerializableFlexBytesEncodable<T, CX> by definition,
    IsValuePropertyDefinitionWrapper<T, TO, CX, DO> {
    override val graphType = PropRef

    override fun ref(parentRef: AnyPropertyReference?) =
        ValueWithFlexBytesPropertyReference(this, parentRef)
}
