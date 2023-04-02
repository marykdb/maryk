package maryk.core.models

import maryk.core.models.definitions.DataModelDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference

open class DataModel<DM: IsValuesDataModel>(
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
) : TypedValuesDataModel<DM>() {
    @Suppress("UNCHECKED_CAST")
    override val Model = DataModelDefinition(
        reservedIndices = reservedIndices,
        reservedNames = reservedNames,
        properties = this,
    ) as DataModelDefinition<DM>

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> invoke(
        parent: AnyOutPropertyReference? = null,
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ) = referenceGetter(this as DM)(parent)

    operator fun <R> invoke(block: DM.() -> R): R {
        @Suppress("UNCHECKED_CAST")
        return block(this as DM)
    }
}
