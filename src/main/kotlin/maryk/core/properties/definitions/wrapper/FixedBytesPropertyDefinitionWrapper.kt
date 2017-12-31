package maryk.core.properties.definitions.wrapper

import maryk.core.objects.IsDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsFixedBytesProperty
import maryk.core.properties.definitions.IsSerializableFixedBytesEncodable
import maryk.core.properties.definitions.key.KeyPartType
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference

/** Contains a Fixed Bytes property definition which can be used for keys.
 * @param index: of definition to encode into ProtoBuf
 * @param name: of definition to display in human readable format
 * @param definition: to be wrapped for DataObject
 * @param getter: to get property value on a DataObject
 *
 * @param T: value type of property
 * @param CX: Context type for property
 * @param D: Type of Definition contained
 * @param DO: Type of DataObject which contains this property
 */
data class FixedBytesPropertyDefinitionWrapper<T: Any, CX: IsPropertyContext, out D: IsSerializableFixedBytesEncodable<T, CX>, in DO: Any>(
        override val index: Int,
        override val name: String,
        override val definition: D,
        override val getter: (DO) -> T?
) :
        IsSerializableFixedBytesEncodable<T, CX> by definition,
        IsPropertyDefinitionWrapper<T, CX, DO>,
        IsValuePropertyDefinitionWrapper<T, CX, DO>,
        IsFixedBytesProperty<T>
{
    override val keyPartType = KeyPartType.Reference

    override fun getRef(parentRef: IsPropertyReference<*, *>?)
            = ValueWithFixedBytesPropertyReference(this, parentRef)

    /** Get the value to be used in a key
     * @param dataModel to use to fetch property if relevant
     * @param dataObject to get property from
     */
    override fun <DO : Any> getValue(dataModel: IsDataModel<DO>, dataObject: DO): T {
        @Suppress("UNCHECKED_CAST")
        return dataModel.properties.getPropertyGetter(
                this.index
        )?.invoke(dataObject) as T
    }
}