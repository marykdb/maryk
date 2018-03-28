package maryk.core.query.filters

import maryk.core.properties.types.IndexedEnum

/** Indexed type of changes */
enum class FilterType(
    override val index: Int
): IndexedEnum<FilterType> {
    AND(0),
    OR(1),
    NOT(2),
    EXISTS(3),
    EQUALS(4),
    LESS_THAN(5),
    LESS_THAN_EQUALS(6),
    GREATER_THAN(7),
    GREATER_THAN_EQUALS(8),
    PREFIX(9),
    RANGE(10),
    REGEX(11),
    VALUE_IN(12)
}
