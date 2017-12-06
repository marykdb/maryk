package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValuePropertyReference

data class DataObjectProperty<T: Any, CX: IsPropertyContext, D: IsSerializableFlexBytesEncodable<T, CX>, DM: Any>(
        override val index: Int,
        override val name: String,
        override val property: D,
        override val getter: (DM) -> T?
) : IsSerializableFlexBytesEncodable<T, CX> by property, IsDataObjectValueProperty<T, CX, DM> {
    override fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>?)
            = ValuePropertyReference(this, parentRefFactory())
}
