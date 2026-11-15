package maryk.datastore.foundationdb.processors

import maryk.foundationdb.Transaction
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.matchesNamedSearchIndex
import maryk.core.properties.definitions.index.matchesNamedSearchIndexPrefix
import maryk.core.properties.definitions.index.matchesNamedSearchIndexRegex
import maryk.core.properties.definitions.index.stringIndexTransform
import maryk.core.query.filters.matchesFilterWithPresence
import maryk.core.query.requests.IsFetchRequest
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.DecryptValue
import maryk.datastore.foundationdb.processors.helpers.matchQualifierPresence
import maryk.datastore.foundationdb.processors.helpers.matchQualifier
import maryk.datastore.foundationdb.processors.helpers.PreparedFilterRead

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
    decryptValue: DecryptValue? = null,
    normalizingIndex: IsIndexable? = null,
    preparedValues: PreparedFilterRead? = null,
    preparedSoftDeleted: Boolean? = null,
) = when {
    toVersion != null && createdVersion != null && createdVersion > toVersion -> true
    this.filterSoftDeleted && (preparedSoftDeleted ?: isSoftDeleted(transaction, tableDirs, toVersion, key, keyOffset, keyLength, decryptValue,
        preparedValues?.get(byteArrayOf(SOFT_DELETE_INDICATOR)))) -> true
    else -> where != null && !matchesFilterWithPresence(
        where,
        valueMatcher = { propertyReference, valueMatcher ->
            transaction.matchQualifier(tableDirs, key, keyOffset, keyLength, propertyReference, toVersion, decryptValue, preparedValues, valueMatcher)
        },
        presenceMatcher = { propertyReference ->
            transaction.matchQualifierPresence(tableDirs, key.copyOfRange(keyOffset, keyOffset + keyLength), propertyReference, toVersion, decryptValue)
        },
        normalizer = { propertyReference, value ->
            val transform = normalizingIndex?.stringIndexTransform(propertyReference) ?: return@matchesFilterWithPresence value
            when (value) {
                is String -> transform.apply(value)
                else -> value
            }
        },
        searchMatcher = { name, value ->
            this.dataModel.matchesNamedSearchIndex(name, value) { propertyReference, valueMatcher ->
                transaction.matchQualifier(tableDirs, key, keyOffset, keyLength, propertyReference, toVersion, decryptValue, matcher = valueMatcher)
            }
        },
        searchPrefixMatcher = { name, value ->
            this.dataModel.matchesNamedSearchIndexPrefix(name, value) { propertyReference, valueMatcher ->
                transaction.matchQualifier(tableDirs, key, keyOffset, keyLength, propertyReference, toVersion, decryptValue, matcher = valueMatcher)
            }
        },
        searchRegexMatcher = { name, regex ->
            this.dataModel.matchesNamedSearchIndexRegex(name, regex) { propertyReference, valueMatcher ->
                transaction.matchQualifier(tableDirs, key, keyOffset, keyLength, propertyReference, toVersion, decryptValue, matcher = valueMatcher)
            }
        }
    )
}
