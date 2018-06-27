package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsDataModel
import maryk.core.properties.graph.PropRefGraphType
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.IsSerializableFixedBytesEncodable
import maryk.core.properties.definitions.key.KeyPartType
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference

/**
 * Contains a Fixed Bytes property [definition] of [D] which can be used for keys.
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class FixedBytesPropertyDefinitionWrapper<T: Any, TO:Any, CX: IsPropertyContext, out D: IsSerializableFixedBytesEncodable<T, CX>, in DO: Any> internal constructor(
    override val index: Int,
    override val name: String,
    override val definition: D,
    override val getter: (DO) -> TO?,
    override val capturer: ((CX, T) -> Unit)? = null,
    override val toSerializable: ((TO?, CX?) -> T?)? = null,
    override val fromSerializable: ((T?) -> TO?)? = null
) :
    IsSerializableFixedBytesEncodable<T, CX> by definition,
    IsPropertyDefinitionWrapper<T, TO, CX, DO>,
    IsValuePropertyDefinitionWrapper<T, TO, CX, DO>,
    FixedBytesProperty<T>()
{
    override val graphType = PropRefGraphType.PropRef
    override val keyPartType = KeyPartType.Reference

    override fun getRef(parentRef: IsPropertyReference<*, *>?) =
        ValueWithFixedBytesPropertyReference(this, parentRef)

    /** Get the value to be used in a key from [dataObject] defined by [dataModel] */
    override fun <DO : Any> getValue(dataModel: IsDataModel<DO>, dataObject: DO): T {
        @Suppress("UNCHECKED_CAST")
        return dataModel.properties.getPropertyGetter(
            this.index
        )?.invoke(dataObject) as T
    }
}
