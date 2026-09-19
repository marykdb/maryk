package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.matchesNamedSearchIndex
import maryk.core.properties.definitions.index.matchesNamedSearchIndexPrefix
import maryk.core.properties.definitions.index.matchesNamedSearchIndexRegex
import maryk.core.properties.definitions.index.stringIndexTransform
import maryk.core.properties.types.Key
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.matchesFilter
import maryk.core.query.requests.IsFetchRequest
import maryk.datastore.memory.records.DataRecord

/**
 * Test if [dataRecord] should be filtered based on given FetchRequest
 * Filters on soft deleted state and given filters.
 * Return true if [dataRecord] should be filtered away.
 */
internal fun <DM : IsRootDataModel> IsFetchRequest<DM, *>.shouldBeFiltered(
    dataRecord: DataRecord<DM>,
    toVersion: HLC?,
    recordFetcher: (IsRootDataModel, Key<*>) -> DataRecord<*>?,
    normalizingIndex: IsIndexable? = null
) = when {
    toVersion != null && dataRecord.firstVersion > toVersion -> true
    this.filterSoftDeleted && dataRecord.isDeleted(toVersion) -> true
    else -> !filterMatches(this.dataModel, normalizingIndex, where, dataRecord, toVersion, recordFetcher)
}

/** Test if [dataRecord] is passing given [filter]. True if filter matches. */
internal fun <DM : IsRootDataModel> filterMatches(
    filter: IsFilter?,
    dataRecord: DataRecord<DM>,
    toVersion: HLC?,
    recordFetcher: (IsRootDataModel, Key<*>) -> DataRecord<*>?
) =
    matchesFilter(
        filter,
        valueMatcher = { propertyReference, valueMatcher ->
            dataRecord.matchQualifier(propertyReference, toVersion, recordFetcher, valueMatcher)
        }
    )

internal fun <DM : IsRootDataModel> filterMatches(
    dataModel: DM,
    normalizingIndex: IsIndexable?,
    filter: IsFilter?,
    dataRecord: DataRecord<DM>,
    toVersion: HLC?,
    recordFetcher: (IsRootDataModel, Key<*>) -> DataRecord<*>?
) =
    matchesFilter(
        filter,
        valueMatcher = { propertyReference, valueMatcher ->
            dataRecord.matchQualifier(propertyReference, toVersion, recordFetcher, valueMatcher)
        },
        normalizer = { propertyReference, value ->
            val transform = normalizingIndex?.stringIndexTransform(propertyReference) ?: return@matchesFilter value
            when (value) {
                is String -> transform.apply(value)
                else -> value
            }
        },
        searchMatcher = { name, value ->
            dataModel.matchesNamedSearchIndex(name, value) { propertyReference, valueMatcher ->
                dataRecord.matchQualifier(propertyReference, toVersion, recordFetcher, valueMatcher)
            }
        },
        searchPrefixMatcher = { name, value ->
            dataModel.matchesNamedSearchIndexPrefix(name, value) { propertyReference, valueMatcher ->
                dataRecord.matchQualifier(propertyReference, toVersion, recordFetcher, valueMatcher)
            }
        },
        searchRegexMatcher = { name, regex ->
            dataModel.matchesNamedSearchIndexRegex(name, regex) { propertyReference, valueMatcher ->
                dataRecord.matchQualifier(propertyReference, toVersion, recordFetcher, valueMatcher)
            }
        }
    )
