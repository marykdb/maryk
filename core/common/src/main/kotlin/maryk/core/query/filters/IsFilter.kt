package maryk.core.query.filters

import maryk.core.properties.definitions.SubModelDefinition

/** Filter */
interface IsFilter {
    val filterType: FilterType
}

internal val mapOfFilterDefinitions = mapOf(
    FilterType.And to SubModelDefinition(dataModel = { And }),
    FilterType.Or to SubModelDefinition(dataModel = { Or }),
    FilterType.Not to SubModelDefinition(dataModel = { Not }),
    FilterType.Exists to SubModelDefinition(dataModel = { Exists }),
    FilterType.Equals to SubModelDefinition(dataModel = { Equals }),
    FilterType.LessThan to SubModelDefinition(dataModel = { LessThan }),
    FilterType.LessThanEquals to SubModelDefinition(dataModel = { LessThanEquals }),
    FilterType.GreaterThan to SubModelDefinition(dataModel = { GreaterThan }),
    FilterType.GreaterThanEquals to SubModelDefinition(dataModel = { GreaterThanEquals }),
    FilterType.Prefix to SubModelDefinition(dataModel = { Prefix }),
    FilterType.Range to SubModelDefinition(dataModel = { Range }),
    FilterType.RegEx to SubModelDefinition(dataModel = { RegEx }),
    FilterType.ValueIn to SubModelDefinition(dataModel = { ValueIn })
)
