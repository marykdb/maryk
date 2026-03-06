package maryk.datastore.foundationdb.processors

import maryk.foundationdb.Transaction
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.hasNormalizeIndex
import maryk.core.properties.definitions.index.normalizeStringForIndex
import maryk.core.query.filters.matchesFilter
import maryk.core.query.requests.IsFetchRequest
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.matchQualifier

/**
 * Test if record at [key] should be filtered based on given FetchRequest
 * Filters on soft deleted state and given filters.
 * Return true if record should be filtered away.
 */
internal fun <DM : IsRootDataModel> IsFetchRequest<DM, *>.shouldBeFiltered(
    transaction: Transaction,
    tableDirs: IsTableDirectories,
    key: ByteArray,
    keyOffset: Int,
    keyLength: Int,
    createdVersion: ULong?, // Can be null in cases when creationVersion is certainly lower than toVersion
    toVersion: ULong?,
    decryptValue: ((ByteArray) -> ByteArray)? = null,
    normalizingIndex: IsIndexable? = null
) = when {
    toVersion != null && createdVersion != null && createdVersion > toVersion -> true
    this.filterSoftDeleted && isSoftDeleted(transaction, tableDirs, toVersion, key, keyOffset, keyLength, decryptValue) -> true
    else -> !matchesFilter(
        where,
        valueMatcher = { propertyReference, valueMatcher ->
            transaction.matchQualifier(tableDirs, key, keyOffset, keyLength, propertyReference, toVersion, decryptValue, valueMatcher)
        },
        normalizer = { propertyReference, value ->
            if (normalizingIndex?.hasNormalizeIndex(propertyReference) != true) {
                value
            } else {
                when (value) {
                    is String -> normalizeStringForIndex(value)
                    else -> value
                }
            }
        }
    )
}
