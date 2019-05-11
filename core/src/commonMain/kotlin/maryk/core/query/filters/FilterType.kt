package maryk.core.query.filters

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum

/** Indexed type of changes */
enum class FilterType(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<FilterType>, IsCoreEnum, TypeEnum<IsFilter> {
    And(1u),
    Or(2u),
    Not(3u),
    Exists(4u),
    Equals(5u),
    LessThan(6u),
    LessThanEquals(7u),
    GreaterThan(8u),
    GreaterThanEquals(9u),
    Prefix(10u),
    Range(11u),
    RegEx(12u),
    ValueIn(13u);

    companion object : IndexedEnumDefinition<FilterType>(
        "FilterType", FilterType::values
    )
}
