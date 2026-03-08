package maryk.datastore.rocksdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.matchesNamedSearchIndex
import maryk.core.properties.definitions.index.stringIndexTransform
import maryk.core.query.filters.matchesFilter
import maryk.core.query.requests.IsFetchRequest
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.matchQualifier
import maryk.rocksdb.ReadOptions

/**
 * Test if record at [key] should be filtered based on given FetchRequest
 * Filters on soft deleted state and given filters.
 * Return true if record should be filtered away.
 */
internal fun <DM : IsRootDataModel> IsFetchRequest<DM, *>.shouldBeFiltered(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray,
    keyOffset: Int,
    keyLength: Int,
    createdVersion: ULong?, // Can be null in cases when creationVersion is certainly lower than toVersion
    toVersion: ULong?,
    normalizingIndex: IsIndexable? = null
) = when {
    toVersion != null && createdVersion != null && createdVersion > toVersion -> true
    this.filterSoftDeleted && isSoftDeleted(dbAccessor, columnFamilies, readOptions, toVersion, key, keyOffset, keyLength) -> true
    else -> !matchesFilter(
        where,
        valueMatcher = { propertyReference, valueMatcher ->
            dbAccessor.matchQualifier(columnFamilies, readOptions, key, keyOffset, keyLength, propertyReference, toVersion, valueMatcher)
        },
        normalizer = { propertyReference, value ->
            val transform = normalizingIndex?.stringIndexTransform(propertyReference) ?: return@matchesFilter value
            when (value) {
                is String -> transform.apply(value)
                else -> value
            }
        },
        searchMatcher = { name, value ->
            this.dataModel.matchesNamedSearchIndex(name, value) { propertyReference, valueMatcher ->
                dbAccessor.matchQualifier(columnFamilies, readOptions, key, keyOffset, keyLength, propertyReference, toVersion, valueMatcher)
            }
        }
    )
}
