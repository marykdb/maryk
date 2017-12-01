package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFixedBytesEncodable

data class DataObjectFixedBytesProperty<T: Any, CX: IsPropertyContext, out D: IsSerializableFixedBytesEncodable<T, CX>, in DM: Any>(
        override val index: Int,
        override val name: String,
        override val property: D,
        override val getter: (DM) -> T?
) :
        IsSerializableFixedBytesEncodable<T, CX> by property,
        IsDataObjectProperty<T, CX, DM>