package maryk.core.properties.definitions.wrapper

import maryk.core.objects.IsDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsFixedBytesProperty
import maryk.core.properties.definitions.IsSerializableFixedBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValuePropertyReference

data class DataObjectFixedBytesProperty<T: Any, CX: IsPropertyContext, out D: IsSerializableFixedBytesEncodable<T, CX>, in DM: Any>(
        override val index: Int,
        override val name: String,
        override val property: D,
        override val getter: (DM) -> T?
) :
        IsSerializableFixedBytesEncodable<T, CX> by property,
        IsDataObjectProperty<T, CX, DM>,
        IsDataObjectValueProperty<T, CX, DM>,
        IsFixedBytesProperty<T>
{
    override fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>?)
            = ValuePropertyReference(this, parentRefFactory())

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

    override fun validate(previousValue: T?, newValue: T?, parentRefFactory: () -> IsPropertyReference<*, *>?) {
        this.property.validateWithRef(previousValue, newValue, { this.getRef(parentRefFactory) })
    }
}