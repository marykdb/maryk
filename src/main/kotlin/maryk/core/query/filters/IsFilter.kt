package maryk.core.query.filters

import maryk.core.properties.definitions.SubModelDefinition

/** Filter */
interface IsFilter {
    val filterType: FilterType
}

internal val mapOfFilterDefinitions = mapOf(
        FilterType.AND.index to SubModelDefinition(
                required = true,
                dataModel = And
        ),
        FilterType.OR.index to SubModelDefinition(
                required = true,
                dataModel = Or
        ),
        FilterType.NOT.index to SubModelDefinition(
                required = true,
                dataModel = Not
        ),
        FilterType.EXISTS.index to SubModelDefinition(
                required = true,
                dataModel = Exists
        ),
        FilterType.EQUALS.index to SubModelDefinition(
                required = true,
                dataModel = Equals
        ),
        FilterType.LESS_THAN.index to SubModelDefinition(
                required = true,
                dataModel = LessThan
        ),
        FilterType.LESS_THAN_EQUALS.index to SubModelDefinition(
                required = true,
                dataModel = LessThanEquals
        ),
        FilterType.GREATER_THAN.index to SubModelDefinition(
                required = true,
                dataModel = GreaterThan
        ),
        FilterType.GREATER_THAN_EQUALS.index to SubModelDefinition(
                required = true,
                dataModel = GreaterThanEquals
        ),
        FilterType.PREFIX.index to SubModelDefinition(
                required = true,
                dataModel = Prefix
        ),
        FilterType.RANGE.index to SubModelDefinition(
                required = true,
                dataModel = Range
        ),
        FilterType.REGEX.index to SubModelDefinition(
                required = true,
                dataModel = RegEx
        ),
        FilterType.VALUE_IN.index to SubModelDefinition(
                required = true,
                dataModel = ValueIn
        )
)