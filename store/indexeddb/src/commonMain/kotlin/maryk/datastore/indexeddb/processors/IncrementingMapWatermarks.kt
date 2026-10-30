package maryk.datastore.indexeddb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.types.Bytes
import maryk.core.values.Values
import maryk.datastore.indexeddb.IndexedDbByteStore
import maryk.datastore.indexeddb.IndexedDbWriteOperation
import maryk.datastore.shared.IncrementingMapMaximum
import maryk.datastore.shared.incrementingMapWatermarkPrefix
import maryk.datastore.shared.encodeIncrementingMapMaximum
import maryk.datastore.shared.migration.legacyIncrementingMapProofKey

internal data class IncrementingMapWatermarkWrites(
    val operations: List<IndexedDbWriteOperation>,
    val advanced: Boolean,
)

internal fun <DM : IsRootDataModel> collectIncrementingMapMaxima(values: Values<DM>): List<IncrementingMapMaximum> = buildList {
    values.writeToStorage { type, qualifier, definition, _ ->
        if (type != MapSize || definition !is IncrementingMapDefinition<*, *, *>) return@writeToStorage
        var readIndex = 0
        @Suppress("UNCHECKED_CAST")
        val reference = values.dataModel.getPropertyReferenceByStorageBytes(qualifier.size, { qualifier[readIndex++] })
            as? IncMapReference<out Comparable<Any>, out Any, *> ?: return@writeToStorage
        val maximum = values[reference].orEmptyComparableMap().keys.maxOrNull() ?: return@writeToStorage
        add(IncrementingMapMaximum(reference, maximum))
    }
}

internal suspend fun readIncrementingMapMaximum(
    byteStore: IndexedDbByteStore,
    modelId: UInt,
    keyBytes: ByteArray,
    reference: IncMapReference<out Comparable<Any>, out Any, *>,
): Comparable<Any>? {
    val encoded = byteStore.get("meta", incrementingMapWatermarkPrefix(modelId, keyBytes) + reference.toStorageByteArray()) ?: return null
    return reference.propertyDefinition.definition.keyDefinition.fromStorageBytes(encoded)
}

internal suspend fun prepareIncrementingMapWatermarks(
    byteStore: IndexedDbByteStore,
    modelId: UInt,
    keyBytes: ByteArray,
    maxima: List<IncrementingMapMaximum>,
    originalMaxima: List<IncrementingMapMaximum> = emptyList(),
): IncrementingMapWatermarkWrites {
    val originalByReference = originalMaxima.associateBy { Bytes(it.reference.toStorageByteArray()) }
    val operations = mutableListOf<IndexedDbWriteOperation>()
    var advanced = false
    for (maximum in maxima) {
        val reference = maximum.reference
        val previous = readIncrementingMapMaximum(byteStore, modelId, keyBytes, reference)
        val original = originalByReference[Bytes(reference.toStorageByteArray())]?.key
        val effectivePrevious = listOfNotNull(previous, original).maxOrNull()
        if (effectivePrevious == null || maximum.key > effectivePrevious) advanced = true
        if (previous == null || maximum.key > previous) {
            operations.put("meta", incrementingMapWatermarkPrefix(modelId, keyBytes) + reference.toStorageByteArray(), encodeIncrementingMapMaximum(maximum))
        }
    }
    return IncrementingMapWatermarkWrites(operations, advanced)
}

internal suspend fun deleteIncrementingMapWatermarks(
    byteStore: IndexedDbByteStore,
    operations: MutableList<IndexedDbWriteOperation>,
    modelId: UInt,
    keyBytes: ByteArray,
) {
    val prefix = incrementingMapWatermarkPrefix(modelId, keyBytes)
    operations.delete("meta", legacyIncrementingMapProofKey(modelId, keyBytes))
    byteStore.scan("meta", startKey = prefix, endKey = keyPrefixUpperBound(prefix), includeEnd = false)
        .forEach { (key, _) -> operations.delete("meta", key) }
}
