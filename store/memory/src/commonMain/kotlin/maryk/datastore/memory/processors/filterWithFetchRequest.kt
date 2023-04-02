package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
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
    recordFetcher: (IsRootDataModel, Key<*>) -> DataRecord<*>?
) = when {
    toVersion != null && dataRecord.firstVersion > toVersion -> true
    this.filterSoftDeleted && dataRecord.isDeleted(toVersion) -> true
    else -> !filterMatches(where, dataRecord, toVersion, recordFetcher)
}

/** Test if [dataRecord] is passing given [filter]. True if filter matches. */
internal fun <DM : IsRootDataModel> filterMatches(
    filter: IsFilter?,
    dataRecord: DataRecord<DM>,
    toVersion: HLC?,
    recordFetcher: (IsRootDataModel, Key<*>) -> DataRecord<*>?
) =
    matchesFilter(filter) { propertyReference, valueMatcher ->
        dataRecord.matchQualifier(propertyReference, toVersion, recordFetcher, valueMatcher)
    }
