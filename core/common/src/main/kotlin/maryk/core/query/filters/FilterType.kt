package maryk.core.query.filters

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/** Indexed type of changes */
enum class FilterType(
    override val index: Int
): IndexedEnum<FilterType> {
    And(1),
    Or(2),
    Not(3),
    Exists(4),
    Equals(5),
    LessThan(6),
    LessThanEquals(7),
    GreaterThan(8),
    GreaterThanEquals(9),
    Prefix(10),
    Range(11),
    RegEx(12),
    ValueIn(13);

    companion object: IndexedEnumDefinition<FilterType>(
        "FilterType", FilterType::values
    )
}
