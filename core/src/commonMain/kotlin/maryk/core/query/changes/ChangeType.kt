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
    ObjectCreate(3u),
    ObjectDelete(4u),
    ListChange(5u),
    SetChange(6u),
    TypeChange(7u),
    IncMapChange(8u),
    IncMapAddition(9u),
    IndexChange(10u);

    companion object : IndexedEnumDefinition<ChangeType>(
        ChangeType::class, { entries }
    )
}
