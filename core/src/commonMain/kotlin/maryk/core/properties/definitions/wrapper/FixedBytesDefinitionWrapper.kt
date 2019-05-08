package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsSerializableFixedBytesEncodable
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference

/**
 * Contains a Fixed Bytes property [definition] of [D] which can be used for keys.
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class FixedBytesDefinitionWrapper<T : Any, TO : Any, CX : IsPropertyContext, out D : IsSerializableFixedBytesEncodable<T, CX>, in DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: D,
    override val getter: (DO) -> TO? = { null },
    override val capturer: ((CX, T) -> Unit)? = null,
    override val toSerializable: ((TO?, CX?) -> T?)? = null,
    override val fromSerializable: ((T?) -> TO?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsSerializableFixedBytesEncodable<T, CX> by definition,
    IsDefinitionWrapper<T, TO, CX, DO>,
    IsValueDefinitionWrapper<T, TO, CX, DO>,
    IsFixedStorageBytesEncodable<T> {
    override val graphType = PropRef

    override fun ref(parentRef: AnyPropertyReference?) =
        ValueWithFixedBytesPropertyReference(this, parentRef)
}
