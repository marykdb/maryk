package maryk.datastore.indexeddb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.matchers.IsQualifierMatcher
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.processors.datastore.matchers.ReferencedQualifierMatcher
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.RegEx
import maryk.core.query.filters.ValueIn
import maryk.core.query.requests.change



internal fun IsQualifierMatcher.referencedMatcher(): ReferencedQualifierMatcher? = when (this) {
    is QualifierExactMatcher -> referencedQualifierMatcher
    is QualifierFuzzyMatcher -> referencedQualifierMatcher
}

internal fun IsQualifierMatcher.referenceForMatch(): AnyPropertyReference? = when (this) {
    is QualifierExactMatcher -> reference
    is QualifierFuzzyMatcher -> reference
}

internal fun IsFilter.hasReferencedQualifier(): Boolean = when (filterType) {
    FilterType.And -> (this as And).filters.any { it.hasReferencedQualifier() }
    FilterType.Or -> (this as Or).filters.any { it.hasReferencedQualifier() }
    FilterType.Not -> (this as Not).filters.any { it.hasReferencedQualifier() }
    FilterType.Exists -> (this as Exists).references.any { it.toQualifierMatcher().referencedMatcher() != null }
    FilterType.Equals -> (this as Equals).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.LessThan -> (this as LessThan).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.LessThanEquals -> (this as LessThanEquals).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.GreaterThan -> (this as GreaterThan).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.GreaterThanEquals -> (this as GreaterThanEquals).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.Prefix -> (this as Prefix).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.Range -> (this as Range).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.RegEx -> (this as RegEx).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.ValueIn -> (this as ValueIn).referenceValuePairs.any { (reference, _) ->
        reference.toQualifierMatcher().referencedMatcher() != null
    }
    FilterType.Matches,
    FilterType.MatchesPrefix,
    FilterType.MatchesRegEx -> false
}

internal fun valuesEqual(actual: Any?, expected: Any?): Boolean = when {
    actual is Collection<*> && expected is Collection<*> -> expected.all { expectedValue ->
        actual.any { it == expectedValue }
    }
    actual is Collection<*> -> actual.any { it == expected }
    expected is Collection<*> -> expected.any { it == actual }
    else -> actual == expected
}

internal fun <DM : IsRootDataModel> ValuesWithMetaData<DM>.toCreationChanges(
    fromVersion: ULong,
    toVersion: ULong?,
    select: RootPropRefGraph<DM>?,
): List<VersionedChanges> {
    if (firstVersion < fromVersion) return emptyList()
    if (toVersion != null && firstVersion > toVersion) return emptyList()

    val changes = (listOf(ObjectCreate) + values.toChanges().toList()).mapNotNull { change ->
        select?.let { change.filterWithSelect(it) } ?: change
    }
    if (changes.isEmpty()) return emptyList()

    return listOf(
        VersionedChanges(
            version = firstVersion,
            changes = changes,
        )
    )
}

