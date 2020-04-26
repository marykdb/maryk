package maryk.core.query.changes

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum

/** Indexed type of changes */
enum class ChangeType(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<ChangeType>, IsCoreEnum, TypeEnum<IsChange> {
    Check(1u),
    Change(2u),
    Delete(3u),
    ObjectCreate(4u),
    ObjectDelete(5u),
    ListChange(6u),
    SetChange(7u),
    TypeChange(8u),
    IncMapChange(9u),
    IncMapAddition(10u),
    IndexChange(11u);

    companion object : IndexedEnumDefinition<ChangeType>(
        ChangeType::class, ChangeType::values
    )
}
