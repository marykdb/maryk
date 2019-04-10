package maryk.core.query.changes

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum

/** Indexed type of changes */
enum class ChangeType(
    override val index: UInt
) : IndexedEnumComparable<ChangeType>, IsCoreEnum {
    Check(1u),
    Change(2u),
    Delete(3u),
    ObjectDelete(4u),
    ListChange(5u),
    SetChange(6u),
    TypeChange(7u);

    companion object : IndexedEnumDefinition<ChangeType>(
        "ChangeType", ChangeType::values
    )
}
