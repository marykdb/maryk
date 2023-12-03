package maryk.core.query.filters

import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.references.IsPropertyReference

/** Filter */
interface IsFilter {
    val filterType: FilterType

    /** Check if filter contains a reference matching [predicate] */
    fun singleReference(predicate: (IsPropertyReference<*, *, *>) -> Boolean): IsPropertyReference<*, *, *>?
}

internal val mapOfFilterDefinitions = mapOf(
    FilterType.And to EmbeddedObjectDefinition(dataModel = { And }),
    FilterType.Or to EmbeddedObjectDefinition(dataModel = { Or }),
    FilterType.Not to EmbeddedObjectDefinition(dataModel = { Not }),
    FilterType.Exists to EmbeddedObjectDefinition(dataModel = { Exists }),
    FilterType.Equals to EmbeddedObjectDefinition(dataModel = { Equals }),
    FilterType.LessThan to EmbeddedObjectDefinition(dataModel = { LessThan }),
    FilterType.LessThanEquals to EmbeddedObjectDefinition(dataModel = { LessThanEquals }),
    FilterType.GreaterThan to EmbeddedObjectDefinition(dataModel = { GreaterThan }),
    FilterType.GreaterThanEquals to EmbeddedObjectDefinition(dataModel = { GreaterThanEquals }),
    FilterType.Prefix to EmbeddedObjectDefinition(dataModel = { Prefix }),
    FilterType.Range to EmbeddedObjectDefinition(dataModel = { Range }),
    FilterType.RegEx to EmbeddedObjectDefinition(dataModel = { RegEx }),
    FilterType.ValueIn to EmbeddedObjectDefinition(dataModel = { ValueIn })
)
