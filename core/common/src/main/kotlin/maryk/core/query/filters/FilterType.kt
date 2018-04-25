package maryk.core.query.filters

import maryk.core.properties.types.IndexedEnum

/** Indexed type of changes */
enum class FilterType(
    override val index: Int
): IndexedEnum<FilterType> {
    And(0),
    Or(1),
    Not(2),
    Exists(3),
    Equals(4),
    LessThan(5),
    LessThanEquals(6),
    GreaterThan(7),
    GreaterThanEquals(8),
    Prefix(9),
    Range(10),
    RegEx(11),
    ValueIn(12)
}
