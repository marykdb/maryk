package maryk.datastore.rocksdb.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.filters.matchesFilter
import maryk.core.query.requests.IsFetchRequest
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.getValue
import maryk.datastore.rocksdb.processors.helpers.matchQualifier
import maryk.rocksdb.ReadOptions

/**
 * Test if record at [key] should be filtered based on given FetchRequest
 * Filters on soft deleted state and given filters.
 * Return true if record should be filtered away.
 */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> IsFetchRequest<DM, P, *>.shouldBeFiltered(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray,
    keyOffset: Int,
    keyLength: Int,
    createdVersion: ULong,
    toVersion: ULong?
) = when {
    toVersion != null && createdVersion > toVersion -> true
    this.filterSoftDeleted && dbAccessor.getValue(columnFamilies, readOptions, toVersion, softDeleteQualifier(key, keyOffset, keyLength)) { b, o, l -> b[l+o-1] == TRUE } ?: false -> true
    else -> !matchesFilter(where) { propertyReference, valueMatcher ->
        dbAccessor.matchQualifier(columnFamilies, readOptions, key, keyOffset, keyLength, propertyReference, toVersion, valueMatcher)
    }
}

private fun softDeleteQualifier(key: ByteArray, keyOffset: Int, keyLength: Int): ByteArray {
    val qualifier = ByteArray(keyLength + 1)
    key.copyInto(qualifier, 0, keyOffset, keyOffset + keyLength)
    qualifier[keyLength] = SOFT_DELETE_INDICATOR
    return qualifier
}
