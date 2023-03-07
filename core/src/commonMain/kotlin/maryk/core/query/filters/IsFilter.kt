package maryk.core.query.filters

import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.references.IsPropertyReference
import kotlin.native.concurrent.SharedImmutable

/** Filter */
interface IsFilter {
    val filterType: FilterType

    /** Check if filter contains a reference matching [predicate] */
    fun singleReference(predicate: (IsPropertyReference<*, *, *>) -> Boolean): IsPropertyReference<*, *, *>?
}

@SharedImmutable
internal val mapOfFilterDefinitions = mapOf(
    FilterType.And to EmbeddedObjectDefinition(dataModel = { And.Model }),
    FilterType.Or to EmbeddedObjectDefinition(dataModel = { Or.Model }),
    FilterType.Not to EmbeddedObjectDefinition(dataModel = { Not.Model }),
    FilterType.Exists to EmbeddedObjectDefinition(dataModel = { Exists.Model }),
    FilterType.Equals to EmbeddedObjectDefinition(dataModel = { Equals.Model }),
    FilterType.LessThan to EmbeddedObjectDefinition(dataModel = { LessThan.Model }),
    FilterType.LessThanEquals to EmbeddedObjectDefinition(dataModel = { LessThanEquals.Model }),
    FilterType.GreaterThan to EmbeddedObjectDefinition(dataModel = { GreaterThan.Model }),
    FilterType.GreaterThanEquals to EmbeddedObjectDefinition(dataModel = { GreaterThanEquals.Model }),
    FilterType.Prefix to EmbeddedObjectDefinition(dataModel = { Prefix.Model }),
    FilterType.Range to EmbeddedObjectDefinition(dataModel = { Range.Model }),
    FilterType.RegEx to EmbeddedObjectDefinition(dataModel = { RegEx.Model }),
    FilterType.ValueIn to EmbeddedObjectDefinition(dataModel = { ValueIn.Model })
)
