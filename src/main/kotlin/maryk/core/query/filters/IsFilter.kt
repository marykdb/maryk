package maryk.core.query.filters

import maryk.core.properties.definitions.SubModelDefinition

/** Filter */
interface IsFilter {
    val filterType: FilterType
}

internal val mapOfFilterDefinitions = mapOf(
        FilterType.AND.index to SubModelDefinition(dataModel = And),
        FilterType.OR.index to SubModelDefinition(dataModel = Or),
        FilterType.NOT.index to SubModelDefinition(dataModel = Not),
        FilterType.EXISTS.index to SubModelDefinition(dataModel = Exists),
        FilterType.EQUALS.index to SubModelDefinition(dataModel = Equals),
        FilterType.LESS_THAN.index to SubModelDefinition(dataModel = LessThan),
        FilterType.LESS_THAN_EQUALS.index to SubModelDefinition(dataModel = LessThanEquals),
        FilterType.GREATER_THAN.index to SubModelDefinition(dataModel = GreaterThan),
        FilterType.GREATER_THAN_EQUALS.index to SubModelDefinition(dataModel = GreaterThanEquals),
        FilterType.PREFIX.index to SubModelDefinition(dataModel = Prefix),
        FilterType.RANGE.index to SubModelDefinition(dataModel = Range),
        FilterType.REGEX.index to SubModelDefinition(dataModel = RegEx),
        FilterType.VALUE_IN.index to SubModelDefinition(dataModel = ValueIn)
)