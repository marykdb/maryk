package maryk.core.query.filters

import maryk.core.properties.definitions.SubModelDefinition

/** Filter */
interface IsFilter {
    val filterType: FilterType
}

internal val mapOfFilterDefinitions = mapOf(
        FilterType.AND to SubModelDefinition(dataModel = { And }),
        FilterType.OR to SubModelDefinition(dataModel = { Or }),
        FilterType.NOT to SubModelDefinition(dataModel = { Not }),
        FilterType.EXISTS to SubModelDefinition(dataModel = { Exists }),
        FilterType.EQUALS to SubModelDefinition(dataModel = { Equals }),
        FilterType.LESS_THAN to SubModelDefinition(dataModel = { LessThan }),
        FilterType.LESS_THAN_EQUALS to SubModelDefinition(dataModel = { LessThanEquals }),
        FilterType.GREATER_THAN to SubModelDefinition(dataModel = { GreaterThan }),
        FilterType.GREATER_THAN_EQUALS to SubModelDefinition(dataModel = { GreaterThanEquals }),
        FilterType.PREFIX to SubModelDefinition(dataModel = { Prefix }),
        FilterType.RANGE to SubModelDefinition(dataModel = { Range }),
        FilterType.REGEX to SubModelDefinition(dataModel = { RegEx }),
        FilterType.VALUE_IN to SubModelDefinition(dataModel = { ValueIn })
)