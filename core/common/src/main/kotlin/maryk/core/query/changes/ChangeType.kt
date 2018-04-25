package maryk.core.query.changes

import maryk.core.properties.types.IndexedEnum

/** Indexed type of changes */
enum class ChangeType(
    override val index: Int
): IndexedEnum<ChangeType> {
    Check(0),
    Change(1),
    Delete(2),
    ObjectDelete(3),
    ListChange(4),
    SetChange(5),
    MapChange(6)
}
