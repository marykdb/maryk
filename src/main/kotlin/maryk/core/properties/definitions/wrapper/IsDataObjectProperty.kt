package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializablePropertyDefinition

interface IsDataObjectProperty<T: Any, in CX:IsPropertyContext, in DM>
    : IsSerializablePropertyDefinition<T, CX> {
    override val index: Int
    override val name: String
    val property: IsSerializablePropertyDefinition<T, CX>
    val getter: (DM) -> T?
}