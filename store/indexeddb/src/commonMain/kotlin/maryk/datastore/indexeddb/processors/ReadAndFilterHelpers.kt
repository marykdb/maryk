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
import maryk.core.properties.references.MapAnyKeyReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.SetAnyValueReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SimpleTypedValueReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.references.TypeReference
import maryk.core.properties.types.Key
import maryk.core.query.ValueRange
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.GeoWithinBox
import maryk.core.query.filters.GeoWithinRadius
import maryk.core.query.filters.GeoWithinPolygon
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.RegEx
import maryk.core.query.filters.ValueIn
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.distanceTo
import maryk.core.properties.types.isWithinBox
import maryk.core.properties.types.isWithinPolygon
import maryk.core.query.filters.matchesFilter
import maryk.core.query.filters.matchesFilterWithPresence
import maryk.core.values.Values
import maryk.datastore.indexeddb.IndexedDbByteStore
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.indexeddb.createObjectRowKeyPrefix
import maryk.datastore.indexeddb.readCurrentValues
import maryk.datastore.indexeddb.readRecord
import maryk.datastore.indexeddb.tableQualifierFromRowKey
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.hasStoredReferenceValue
import maryk.datastore.shared.hasStoredValuePayload
import maryk.datastore.shared.helpers.convertToValueOrNull
import maryk.datastore.shared.readStoredReferenceValue
import maryk.datastore.shared.requiresStorageParentMarker
import maryk.datastore.shared.requiresStoredReferenceStructure
import maryk.datastore.shared.storageReferenceParents
import maryk.datastore.shared.storedTypeIndex
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
    uniqueStoreName: String,
    keyBytes: ByteArray,
    uniqueStore: IndexedDbByteStore = byteStore,
    tableRows: List<Pair<ByteArray, ByteArray>>? = null,
): List<IndexedDbUniqueRow> {
    val rows = mutableListOf<IndexedDbUniqueRow>()
    for ((rowKey, rowValue) in tableRows ?: scanTableRows(tableStoreName, keyBytes)) {
        val qualifier = tableQualifierFromRowKey(rowKey, keyBytes)
        var index = 0
        val reference = dataModel.getPropertyReferenceByStorageBytes(
            length = qualifier.size,
            reader = { qualifier[index++] }
        )
        val definition = reference.comparablePropertyDefinition
        if (definition is IsComparableDefinition<*, *> && definition.unique) {
            val encodedValue = sensitiveFields.decryptValueIfNeeded(modelId, keyBytes, qualifier, rowValue)
            val uniqueKeys = sensitiveFields.mapUniqueValueByteCandidates(modelId, qualifier, encodedValue)
                .map { uniqueValue -> createUniqueRowKey(qualifier, uniqueValue) }
            val existingKeys = uniqueKeys.filter { candidateKey ->
                uniqueStore.get(uniqueStoreName, candidateKey)?.contentEquals(keyBytes) == true
            }
            rows += IndexedDbUniqueRow(
                uniqueKey = uniqueKeys.first(),
                keyBytes = keyBytes,
                qualifier = qualifier,
                candidateKeys = uniqueKeys,
                existingKeys = existingKeys,
            )
        }
    }
    return rows
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.validateUniqueRows(
    dataModel: DM,
    keyBytes: ByteArray,
    uniqueStoreName: String,
    uniqueRows: List<IndexedDbUniqueRow>,
) {
    for (row in uniqueRows) {
        for (candidateKey in row.candidateKeys) {
            val existingKey = byteStore.get(uniqueStoreName, candidateKey) ?: continue
            if (!existingKey.contentEquals(keyBytes)) {
                throw UniqueException(row.qualifier, dataModel.key(existingKey))
            }
        }
    }
}

internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.readCurrentValuesDecrypted(
    byteStore: IndexedDbByteStore,
    dataModel: DM,
    tableStoreName: String,
    keyBytes: ByteArray,
    select: RootPropRefGraph<DM>?,
): Values<DM>? = byteStore.readCurrentValues(dataModel, tableStoreName, keyBytes, select) { qualifier, value ->
    sensitiveFields.decryptValueIfNeeded(getDataModelId(dataModel), keyBytes, qualifier, value)
}

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
) { qualifier, value ->
    sensitiveFields.decryptValueIfNeeded(getDataModelId(dataModel), keyBytes, qualifier, value)
}
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
) { qualifier, value ->
    sensitiveFields.decryptValueIfNeeded(getDataModelId(dataModel), keyBytes, qualifier, value)
}
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
) { qualifier, value ->
    sensitiveFields.decryptValueIfNeeded(getDataModelId(dataModel), keyBytes, qualifier, value)
}
internal suspend fun <DM : IsRootDataModel> IndexedDbDataStore.valuesMatchFilter(
    dataModel: DM,
    values: Values<DM>,
    filter: IsFilter?,
    toVersion: ULong?,
    normalizingIndex: IsIndexable? = null,
): Boolean {
    if (filter == null) return true
    if (!filter.hasReferencedQualifier()) {
        return matchesFilterWithPresence(
            filter,
            valueMatcher = { propertyReference, valueMatcher ->
                @Suppress("UNCHECKED_CAST")
                val value = values[propertyReference as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>]

                if (value is List<*> && propertyReference.toQualifierMatcher() is QualifierFuzzyMatcher) {
                    value.any { valueMatcher(it) }
                } else {
                    valueMatcher(value)
                }
            },
            presenceMatcher = { propertyReference ->
                @Suppress("UNCHECKED_CAST")
                val value = values[propertyReference as IsPropertyReference<Any, *, *>]
                if (value is List<*> && propertyReference.toQualifierMatcher() is QualifierFuzzyMatcher) {
                    value.any { it != null }
                } else value != null
            },
            normalizer = { propertyReference, value ->
                val transform = normalizingIndex?.stringIndexTransform(propertyReference) ?: return@matchesFilterWithPresence value
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
    FilterType.Equals -> (filter as Equals).referenceValuePairs.all { pair ->
        matchPropertyReference(dataModel, values, pair.reference, toVersion) { actual ->
            matchesFilter(Equals(pair), valueMatcher = { _, matcher -> matcher(actual) })
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
    FilterType.ValueIn -> (filter as ValueIn).referenceValuePairs.all { pair ->
        matchPropertyReference(dataModel, values, pair.reference, toVersion) { actual ->
            matchesFilter(ValueIn(pair), valueMatcher = { _, matcher -> matcher(actual) })
        }
    }
    FilterType.GeoWithinBox -> (filter as GeoWithinBox).let { geoFilter ->
        matchPropertyReference(dataModel, values, geoFilter.reference, toVersion) { actual ->
            actual is GeoPoint && actual.isWithinBox(
                geoFilter.southWest.latitude,
                geoFilter.southWest.longitude,
                geoFilter.northEast.latitude,
                geoFilter.northEast.longitude,
            )
        }
    }
    FilterType.GeoWithinRadius -> (filter as GeoWithinRadius).let { geoFilter ->
        matchPropertyReference(dataModel, values, geoFilter.reference, toVersion) { actual ->
            actual is GeoPoint && geoFilter.center.distanceTo(actual) <= geoFilter.radiusMeters
        }
    }
    FilterType.GeoWithinPolygon -> (filter as GeoWithinPolygon).let { geoFilter ->
        matchPropertyReference(dataModel, values, geoFilter.reference, toVersion) { actual ->
            actual is GeoPoint && actual.isWithinPolygon(geoFilter.vertices)
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

    return if (value is List<*> && qualifierMatcher is QualifierFuzzyMatcher) {
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

    return matchQualifierOnRows(
        modelId = getDataModelId(referencedDataModel),
        keyBytes = referencedKey.bytes,
        rows = rows,
        qualifierMatcher = referencedMatcher.qualifierMatcher,
        valueMatcher = valueMatcher,
    )
}

internal suspend fun IndexedDbDataStore.readStorageRows(
    dataModel: IsRootDataModel,
    keyBytes: ByteArray,
    toVersion: ULong?,
): List<Pair<ByteArray, ByteArray>>? {
    val modelId = getDataModelId(dataModel)
    if (toVersion != null) {
        val snapshot = byteStore.readHistoricSnapshotBytes("ht:$modelId", keyBytes, toVersion) ?: return null
        return decodeHistoricSnapshot(snapshot).second
    }

    val snapshot = byteStore.get("k:$modelId", keyBytes)
    if (snapshot != null && snapshot.size > 17) {
        val (meta, rows) = decodeCurrentSnapshot(snapshot)
        if (!meta.isDeleted || rows.isNotEmpty()) return rows
    }

    return byteStore.scanObjectScopedRows("t:$modelId", keyBytes).map { (rowKey, rowValue) ->
        tableQualifierFromRowKey(rowKey, keyBytes) to rowValue
    }
}

internal suspend fun IndexedDbDataStore.matchQualifierOnRows(
    modelId: UInt,
    keyBytes: ByteArray,
    rows: List<Pair<ByteArray, ByteArray>>,
    qualifierMatcher: IsQualifierMatcher,
    valueMatcher: (Any?) -> Boolean,
): Boolean {
    val reference = qualifierMatcher.referenceForMatch() ?: return false
    val model = dataModelsById.getValue(modelId)

    suspend fun decrypted(qualifier: ByteArray, bytes: ByteArray): ByteArray =
        sensitiveFields.decryptValueIfNeeded(modelId, keyBytes, qualifier, bytes)

    suspend fun activeType(actualReference: AnyPropertyReference): Boolean {
        for (part in (listOf(actualReference) + storageReferenceParents(actualReference)).asReversed()) {
            val expected = when (part) {
                is TypedValueReference<*, *, *> -> part.type.index
                is SimpleTypedValueReference<*, *, *> -> part.type.index
                else -> continue
            }
            val parent = when (part) {
                is TypedValueReference<*, *, *> -> part.parentReference
                is SimpleTypedValueReference<*, *, *> -> part.parentReference
                else -> null
            } ?: return false
            val qualifier = parent.toStorageByteArray()
            val bytes = rows.firstOrNull { it.first.contentEquals(qualifier) }?.second ?: return false
            if (storedTypeIndex(decrypted(qualifier, bytes)) != expected) return false
        }
        return true
    }

    suspend fun structure(actualReference: AnyPropertyReference): Any? {
        val qualifier = actualReference.toStorageByteArray()
        val raw = rows.firstOrNull { it.first.contentEquals(qualifier) }?.second ?: return null
        val selected = mutableListOf<Pair<ByteArray, ByteArray>>()
        val value = decrypted(qualifier, raw)
        if (!hasStoredReferenceValue(actualReference, value, 0, value.size)) return null
        for (parent in storageReferenceParents(actualReference)) {
            val parentQualifier = parent.toStorageByteArray()
            val parentRaw = rows.firstOrNull { it.first.contentEquals(parentQualifier) }?.second
            val parentValue = parentRaw?.let { decrypted(parentQualifier, it) }
            if (parentValue != null && hasStoredReferenceValue(parent, parentValue, 0, parentValue.size)) {
                selected += parentQualifier to parentValue
            } else if (requiresStorageParentMarker(parent)) return null
        }
        for ((childQualifier, childValue) in rows) {
            if (childQualifier.matchesRangePart(0, qualifier)) selected += childQualifier to decrypted(childQualifier, childValue)
        }
        return model.readStoredReferenceValue(actualReference, selected)
    }

    for ((qualifier, valueBytes) in rows) {
        val matched = when (qualifierMatcher) {
            is QualifierExactMatcher -> qualifier.contentEquals(qualifierMatcher.qualifier)
            is QualifierFuzzyMatcher -> qualifierMatcher.isMatch(qualifier, 0) == MATCH
        }
        if (!matched) continue
        var index = 0
        val actualReference = if (reference is SimpleTypedValueReference<*, *, *> || reference is TypeReference<*, *, *>) reference else
            model.getPropertyReferenceByStorageBytes(qualifier.size, { qualifier[index++] })
        if (!activeType(actualReference) || !hasStoredValuePayload(actualReference.comparablePropertyDefinition, valueBytes, 0, valueBytes.size)) continue
        val value = when {
            (reference is MapAnyKeyReference<*, *, *> || reference is MapKeyReference<*, *, *>) && actualReference is MapValueReference<*, *, *> -> actualReference.key
            reference is SetAnyValueReference<*, *> && actualReference is SetItemReference<*, *> -> actualReference.value
            requiresStoredReferenceStructure(actualReference) -> structure(actualReference)
            else -> {
                @Suppress("UNCHECKED_CAST")
                decrypted(qualifier, valueBytes).convertToValueOrNull(reference as IsPropertyReference<Any, *, *>)
            }
        }
        if (valueMatcher(value)) return true
    }
    return false
}
