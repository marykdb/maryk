package maryk.core.query.changes

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/** Indexed type of changes */
enum class ChangeType(
    override val index: Int
): IndexedEnum<ChangeType> {
    Check(1),
    Change(2),
    Delete(3),
    ObjectDelete(4),
    ListChange(5),
    SetChange(6),
    MapChange(7),
    TypeChange(8);

    companion object: IndexedEnumDefinition<ChangeType>(
        "ChangeType", ChangeType::values
    )
}
