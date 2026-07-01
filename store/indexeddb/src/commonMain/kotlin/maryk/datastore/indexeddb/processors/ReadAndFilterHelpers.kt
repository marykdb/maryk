package maryk.datastore.indexeddb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.matchers.FuzzyMatchResult.MATCH
import maryk.core.processors.datastore.matchers.IsQualifierMatcher
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.processors.datastore.matchers.ReferencedQualifierMatcher
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.matchesNamedSearchIndex
import maryk.core.properties.definitions.index.matchesNamedSearchIndexPrefix
import maryk.core.properties.definitions.index.matchesNamedSearchIndexRegex
import maryk.core.properties.definitions.index.stringIndexTransform
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.Key
import maryk.core.query.ValueRange
import maryk.core.query.ValuesWithMetaData
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
import maryk.core.query.filters.matchesFilter
import maryk.core.values.Values
import maryk.datastore.indexeddb.IndexedDbByteStore
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.indexeddb.createObjectRowKeyPrefix
import maryk.datastore.indexeddb.decodeStorageValue
import maryk.datastore.indexeddb.readCurrentValues
import maryk.datastore.indexeddb.readRecord
import maryk.datastore.indexeddb.tableQualifierFromRowKey
import maryk.datastore.shared.UniqueException
import maryk.lib.extensions.compare.matchesRangePart

internal suspend fun IndexedDbDataStore.scanTableRows(
    tableStoreName: String,
    keyBytes: ByteArray,
): List<Pair<ByteArray, ByteArray>> {
    val rowKeyPrefix = createObjectRowKeyPrefix(keyBytes)
    return byteStore.scan(
        storeName = tableStoreName,
        startKey = rowKeyPrefix,
        endKey = keyPrefixUpperBound(rowKeyPrefix),
        includeEnd = false,
    ).filter { (rowKey, _) ->
        rowKey.matchesRangePart(0, rowKeyPrefix, sourceLength = rowKey.size, length = rowKeyPrefix.size)
    }
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.collectCurrentIndexRows(
    dataModel: DM,
    keyBytes: ByteArray,
): List<ByteArray> {
    val values = readCurrentValuesDecrypted(byteStore, dataModel, "t:${getDataModelId(dataModel)}", keyBytes, null)
        ?: return emptyList()
    val rows = mutableListOf<ByteArray>()
    dataModel.Meta.indexes?.forEach { index ->
        index.toStorageByteArraysForIndex(values, keyBytes).forEach { valueAndKey ->
            rows += createIndexRowKey(index.referenceStorageByteArray.bytes, valueAndKey)
        }
    }
    return rows
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.collectCurrentUniqueRows(
    dataModel: DM,
    modelId: UInt,
    tableStoreName: String,
    keyBytes: ByteArray,
): List<Triple<ByteArray, ByteArray, ByteArray>> {
    val rows = mutableListOf<Triple<ByteArray, ByteArray, ByteArray>>()
    for ((rowKey, rowValue) in scanTableRows(tableStoreName, keyBytes)) {
        val qualifier = tableQualifierFromRowKey(rowKey, keyBytes)
        var index = 0
        val reference = dataModel.getPropertyReferenceByStorageBytes(
            length = qualifier.size,
            reader = { qualifier[index++] }
        )
        val definition = reference.comparablePropertyDefinition
        if (definition is IsComparableDefinition<*, *> && definition.unique) {
            val encodedValue = sensitiveFields.decryptValueIfNeeded(rowValue)
            val uniqueValue = sensitiveFields.mapUniqueValueBytes(modelId, qualifier, encodedValue)
            rows += Triple(createUniqueRowKey(qualifier, uniqueValue), keyBytes, qualifier)
        }
    }
    return rows
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.validateUniqueRows(
    dataModel: DM,
    keyBytes: ByteArray,
    uniqueStoreName: String,
    uniqueRows: List<Triple<ByteArray, ByteArray, ByteArray>>,
) {
    for ((uniqueKey, _, qualifier) in uniqueRows) {
        val existingKey = byteStore.get(uniqueStoreName, uniqueKey) ?: continue
        if (!existingKey.contentEquals(keyBytes)) {
            throw UniqueException(qualifier, dataModel.key(existingKey))
        }
    }
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.readCurrentValuesDecrypted(
    byteStore: IndexedDbByteStore,
    dataModel: DM,
    tableStoreName: String,
    keyBytes: ByteArray,
    select: RootPropRefGraph<DM>?,
): Values<DM>? = byteStore.readCurrentValues(dataModel, tableStoreName, keyBytes, select, sensitiveFields::decryptValueIfNeeded)

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.readRecordDecrypted(
    byteStore: IndexedDbByteStore,
    dataModel: DM,
    keyStoreName: String,
    tableStoreName: String,
    keyBytes: ByteArray,
    select: RootPropRefGraph<DM>?,
): ValuesWithMetaData<DM>? = byteStore.readRecord(
    dataModel,
    keyStoreName,
    tableStoreName,
    keyBytes,
    select,
    sensitiveFields::decryptValueIfNeeded,
)

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.readCurrentSnapshotDecrypted(
    byteStore: IndexedDbByteStore,
    dataModel: DM,
    keyStoreName: String,
    keyBytes: ByteArray,
    select: RootPropRefGraph<DM>?,
): ValuesWithMetaData<DM>? = byteStore.readCurrentSnapshot(
    dataModel,
    keyStoreName,
    keyBytes,
    select,
    sensitiveFields::decryptValueIfNeeded,
)

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.readHistoricRecordDecrypted(
    byteStore: IndexedDbByteStore,
    dataModel: DM,
    storeName: String,
    keyBytes: ByteArray,
    toVersion: ULong,
    select: RootPropRefGraph<DM>?,
): ValuesWithMetaData<DM>? = byteStore.readHistoricRecord(
    dataModel,
    storeName,
    keyBytes,
    toVersion,
    select,
    sensitiveFields::decryptValueIfNeeded,
)

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.valuesMatchFilter(
    dataModel: DM,
    values: Values<DM>,
    filter: IsFilter?,
    toVersion: ULong?,
    normalizingIndex: IsIndexable? = null,
): Boolean {
    if (filter == null) return true
    if (!filter.hasReferencedQualifier()) {
        if (normalizingIndex == null) return values.matches(filter)
        return matchesFilter(
            filter,
            valueMatcher = { propertyReference, valueMatcher ->
                @Suppress("UNCHECKED_CAST")
                val value = values[propertyReference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>]

                if (value is List<*> && propertyReference !is ListReference<*, *>) {
                    value.any { valueMatcher(it) }
                } else {
                    valueMatcher(value)
                }
            },
            normalizer = { propertyReference, value ->
                val transform = normalizingIndex.stringIndexTransform(propertyReference) ?: return@matchesFilter value
                when (value) {
                    is String -> transform.apply(value)
                    else -> value
                }
            },
            searchMatcher = { name, value ->
                dataModel.matchesNamedSearchIndex(name, value) { propertyReference, valueMatcher ->
                    @Suppress("UNCHECKED_CAST")
                    val actualValue = values[propertyReference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>]
                    valueMatcher(actualValue)
                }
            },
            searchPrefixMatcher = { name, value ->
                dataModel.matchesNamedSearchIndexPrefix(name, value) { propertyReference, valueMatcher ->
                    @Suppress("UNCHECKED_CAST")
                    val actualValue = values[propertyReference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>]
                    valueMatcher(actualValue)
                }
            },
            searchRegexMatcher = { name, regex ->
                dataModel.matchesNamedSearchIndexRegex(name, regex) { propertyReference, valueMatcher ->
                    @Suppress("UNCHECKED_CAST")
                    val actualValue = values[propertyReference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>]
                    valueMatcher(actualValue)
                }
            },
        )
    }
    return valuesMatchReferencedFilter(dataModel, values, filter, toVersion)
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.valuesMatchReferencedFilter(
    dataModel: DM,
    values: Values<DM>,
    filter: IsFilter,
    toVersion: ULong?,
): Boolean = when (filter.filterType) {
    FilterType.And -> (filter as And).filters.all {
        valuesMatchReferencedFilter(dataModel, values, it, toVersion)
    }
    FilterType.Or -> (filter as Or).filters.any {
        valuesMatchReferencedFilter(dataModel, values, it, toVersion)
    }
    FilterType.Not -> (filter as Not).filters.none {
        valuesMatchReferencedFilter(dataModel, values, it, toVersion)
    }
    FilterType.Exists -> (filter as Exists).references.all { reference ->
        matchPropertyReference(dataModel, values, reference, toVersion) { it != null }
    }
    FilterType.Equals -> (filter as Equals).referenceValuePairs.all { (reference, value) ->
        matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
            valuesEqual(actual, value)
        }
    }
    FilterType.LessThan -> (filter as LessThan).referenceValuePairs.all { (reference, value) ->
        @Suppress("UNCHECKED_CAST")
        matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
            actual != null && (value as Comparable<Any>) > actual
        }
    }
    FilterType.LessThanEquals -> (filter as LessThanEquals).referenceValuePairs.all { (reference, value) ->
        @Suppress("UNCHECKED_CAST")
        matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
            actual != null && (value as Comparable<Any>) >= actual
        }
    }
    FilterType.GreaterThan -> (filter as GreaterThan).referenceValuePairs.all { (reference, value) ->
        @Suppress("UNCHECKED_CAST")
        matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
            actual != null && (value as Comparable<Any>) < actual
        }
    }
    FilterType.GreaterThanEquals -> (filter as GreaterThanEquals).referenceValuePairs.all { (reference, value) ->
        @Suppress("UNCHECKED_CAST")
        matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
            actual != null && (value as Comparable<Any>) <= actual
        }
    }
    FilterType.Prefix -> (filter as Prefix).referenceValuePairs.all { (reference, prefix) ->
        matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
            when (actual) {
                is Collection<*> -> actual.any { it is String && it.startsWith(prefix) }
                is String -> actual.startsWith(prefix)
                else -> false
            }
        }
    }
    FilterType.Range -> (filter as Range).referenceValuePairs.all { (reference, range) ->
        @Suppress("UNCHECKED_CAST")
        matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
            val comparable = actual as? Comparable<Any>
            comparable != null && comparable in range as ValueRange<Comparable<Any>>
        }
    }
    FilterType.RegEx -> (filter as RegEx).referenceValuePairs.all { (reference, regex) ->
        matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
            when (actual) {
                is Collection<*> -> actual.any { it is String && regex.matches(it) }
                is String -> regex.matches(actual)
                else -> false
            }
        }
    }
    FilterType.ValueIn -> (filter as ValueIn).referenceValuePairs.all { (reference, expectedValues) ->
        matchPropertyReference(dataModel, values, reference, toVersion) { actual ->
            actual != null && expectedValues.any { valuesEqual(actual, it) }
        }
    }
    FilterType.Matches,
    FilterType.MatchesPrefix,
    FilterType.MatchesRegEx -> values.matches(filter)
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.matchPropertyReference(
    dataModel: DM,
    values: Values<DM>,
    propertyReference: AnyPropertyReference,
    toVersion: ULong?,
    valueMatcher: (Any?) -> Boolean,
): Boolean {
    val qualifierMatcher = propertyReference.toQualifierMatcher()
    val referencedMatcher = qualifierMatcher.referencedMatcher()
    if (referencedMatcher != null) {
        return matchReferencedProperty(values, referencedMatcher, toVersion, valueMatcher)
    }

    @Suppress("UNCHECKED_CAST")
    val value = values[propertyReference as IsPropertyReference<Any, *, *>]

    return if (value is List<*> && propertyReference !is ListReference<*, *>) {
        value.any { valueMatcher(it) }
    } else {
        valueMatcher(value)
    }
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.matchReferencedProperty(
    values: Values<DM>,
    referencedMatcher: ReferencedQualifierMatcher,
    toVersion: ULong?,
    valueMatcher: (Any?) -> Boolean,
): Boolean {
    val reference = referencedMatcher.reference
    @Suppress("UNCHECKED_CAST")
    val referencedKey = values[reference as IsPropertyReference<Any, *, *>] as? Key<*> ?: return false
    val referencedDataModel = reference.propertyDefinition.definition.dataModel
    val rows = readStorageRows(referencedDataModel, referencedKey.bytes, toVersion) ?: return false

    return matchQualifierOnRows(rows, referencedMatcher.qualifierMatcher, valueMatcher)
}

internal suspend fun IndexedDbDataStore.readStorageRows(
    dataModel: IsRootDataModel,
    keyBytes: ByteArray,
    toVersion: ULong?,
): List<Pair<ByteArray, ByteArray>>? {
    val modelId = getDataModelId(dataModel)
    if (toVersion != null) {
        val rowKeyPrefix = createObjectRowKeyPrefix(keyBytes)
        val rows = byteStore.scan(
            storeName = "ht:$modelId",
            startKey = createHistoricSnapshotRowKey(keyBytes, toVersion),
            endKey = keyPrefixUpperBound(rowKeyPrefix),
            includeEnd = false,
            limit = 1u,
        ).filter { (rowKey, _) ->
            rowKey.matchesRangePart(0, rowKeyPrefix, sourceLength = rowKey.size, length = rowKeyPrefix.size)
        }
        val snapshot = rows.firstOrNull()?.second ?: return null
        return decodeHistoricSnapshot(snapshot).second
    }

    val snapshot = byteStore.get("k:$modelId", keyBytes)
    if (snapshot != null && snapshot.size > 17) {
        return decodeCurrentSnapshot(snapshot).second
    }

    return byteStore.scanObjectScopedRows("t:$modelId", keyBytes).map { (rowKey, rowValue) ->
        tableQualifierFromRowKey(rowKey, keyBytes) to rowValue
    }
}

internal suspend fun IndexedDbDataStore.matchQualifierOnRows(
    rows: List<Pair<ByteArray, ByteArray>>,
    qualifierMatcher: IsQualifierMatcher,
    valueMatcher: (Any?) -> Boolean,
): Boolean {
    val reference = qualifierMatcher.referenceForMatch() ?: return false
    val referenceForCache = reference as? IsPropertyReferenceForCache<*, *> ?: return false

    for ((qualifier, valueBytes) in rows) {
        val matched = when (qualifierMatcher) {
            is QualifierExactMatcher -> qualifier.contentEquals(qualifierMatcher.qualifier)
            is QualifierFuzzyMatcher -> qualifierMatcher.isMatch(qualifier, 0) == MATCH
        }
        if (!matched) continue

        if (valueMatcher(decodeStorageValue(referenceForCache, sensitiveFields.decryptValueIfNeeded(valueBytes)))) {
            return true
        }
    }
    return false
}

