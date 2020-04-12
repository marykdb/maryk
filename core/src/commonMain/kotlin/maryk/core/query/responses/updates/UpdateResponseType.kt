package maryk.core.query.responses.updates

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.query.filters.IsFilter

/** Indexed type of update responses */
enum class UpdateResponseType(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<UpdateResponseType>, IsCoreEnum, TypeEnum<IsUpdateResponse<*, *>> {
    Addition(1u),
    Change(2u),
    Removal(3u);

    companion object : IndexedEnumDefinition<UpdateResponseType>(
        UpdateResponseType::class, UpdateResponseType::values
    )
}
