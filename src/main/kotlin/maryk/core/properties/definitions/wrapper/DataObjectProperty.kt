package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializablePropertyDefinition

data class DataObjectProperty<T: Any, CX: IsPropertyContext, D: IsSerializablePropertyDefinition<T, CX>, DM: Any>(
        override val index: Int,
        override val name: String,
        override val property: D,
        override val getter: (DM) -> T?
) : IsSerializablePropertyDefinition<T, CX> by property, IsDataObjectProperty<T, CX, DM>


