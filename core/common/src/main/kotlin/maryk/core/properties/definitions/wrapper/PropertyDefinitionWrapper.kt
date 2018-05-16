package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValuePropertyReference

/**
 * Contains a Flex bytes property [definition] of type [T] which cannot be used in keys or ValueObjects
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class PropertyDefinitionWrapper<T: Any, TO:Any, CX: IsPropertyContext, D: IsSerializableFlexBytesEncodable<T, CX>, DO: Any> internal constructor(
    override val index: Int,
    override val name: String,
    override val definition: D,
    override val getter: (DO) -> TO?,
    override val toSerializable: ((TO?) -> T?)? = null,
    override val fromSerializable: ((T?) -> TO?)? = null,
    override val capturer: ((CX, T) -> Unit)? = null
) :
    IsSerializableFlexBytesEncodable<T, CX> by definition,
    IsValuePropertyDefinitionWrapper<T, TO, CX, DO>
{
    override fun getRef(parentRef: IsPropertyReference<*, *>?) =
        ValuePropertyReference(this, parentRef)
}
