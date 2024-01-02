package maryk.datastore.hbase.processors

import maryk.core.models.IsRootDataModel
import maryk.core.query.filters.matchesFilter
import maryk.core.query.requests.IsFetchRequest
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.softDeleteIndicator
import maryk.datastore.hbase.trueIndicator
import org.apache.hadoop.hbase.client.Result

/**
 * Test if record at [key] should be filtered based on given FetchRequest
 * Filters on soft deleted state and given filters.
 * Return true if record should be filtered away.
 */
@Suppress("UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")
internal fun <DM : IsRootDataModel> IsFetchRequest<DM, *>.shouldBeFiltered(
    result: Result,
    key: ByteArray,
    keyOffset: Int,
    keyLength: Int,
    createdVersion: ULong?, // Can be null in cases when creationVersion is certainly lower than toVersion
    toVersion: ULong?
) = when {
    toVersion != null && createdVersion != null && createdVersion > toVersion -> true
    this.filterSoftDeleted && result.getValue(dataColumnFamily, softDeleteIndicator).contentEquals(trueIndicator) -> true
    else -> !matchesFilter(where) { propertyReference, valueMatcher ->
        false
//        dbAccessor.matchQualifier(columnFamilies, readOptions, key, keyOffset, keyLength, propertyReference, toVersion, valueMatcher)
    }
}
