package maryk.core.properties.definitions.wrapper

import maryk.core.objects.IsDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsFixedBytesProperty
import maryk.core.properties.definitions.IsSerializableFixedBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValuePropertyReference

/** Contains a Fixed Bytes property definition which can be used for keys.
 * @param index: of definition to encode into protobuf
 * @param name: of definition to display in human readable format
 * @param definition: to be wrapped for DataObject
 * @param getter: to get property value on a DataObject
 *
 * @param T: value type of property
 * @param CX: Context type for property
 * @param D: Type of Definition contained
 * @param DM: Type of DataModel which contains this property
 */
data class FixedBytesPropertyDefinitionWrapper<T: Any, CX: IsPropertyContext, out D: IsSerializableFixedBytesEncodable<T, CX>, in DM: Any>(
        override val index: Int,
        override val name: String,
        override val definition: D,
        override val getter: (DM) -> T?
) :
        IsSerializableFixedBytesEncodable<T, CX> by definition,
        IsPropertyDefinitionWrapper<T, CX, DM>,
        IsValuePropertyDefinitionWrapper<T, CX, DM>,
        IsFixedBytesProperty<T>
{
    override fun getRef(parentRef: IsPropertyReference<*, *>?)
            = ValuePropertyReference(this, parentRef)

    /** Get the value to be used in a key
     * @param dataModel to use to fetch property if relevant
     * @param dataObject to get property from
     */
    override fun <DM : Any> getValue(dataModel: IsDataModel<DM>, dataObject: DM): T {
        @Suppress("UNCHECKED_CAST")
        return dataModel.getPropertyGetter(
                this.index
        )?.invoke(dataObject) as T
    }
}