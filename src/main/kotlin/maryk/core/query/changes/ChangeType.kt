package maryk.core.query.changes

import maryk.core.properties.types.IndexedEnum

/** Indexed type of changes */
enum class ChangeType(
        override val index: Int
): IndexedEnum<ChangeType> {
    PROP_CHECK(0),
    PROP_CHANGE(1),
    PROP_DELETE(2),
    OBJECT_DELETE(3),
    LIST_CHANGE(4),
    SET_CHANGE(5),
    MAP_CHANGE(6)
}
