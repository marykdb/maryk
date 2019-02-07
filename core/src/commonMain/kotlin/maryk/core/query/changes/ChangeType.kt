package maryk.core.query.changes

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/** Indexed type of changes */
enum class ChangeType(
    override val index: UInt
): IndexedEnum<ChangeType> {
    Check(1u),
    Change(2u),
    Delete(3u),
    ObjectDelete(4u),
    ListChange(5u),
    SetChange(6u),
    TypeChange(7u);

    companion object: IndexedEnumDefinition<ChangeType>(
        "ChangeType", ChangeType::values
    )
}
