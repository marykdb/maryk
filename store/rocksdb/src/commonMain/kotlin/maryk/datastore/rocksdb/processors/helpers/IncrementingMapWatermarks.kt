package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.references.IncMapReference
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.shared.decodeIncrementingMapMaximum
import maryk.datastore.shared.encodeIncrementingMapMaximum
import maryk.datastore.shared.incrementingMapKeysForQualifier
import maryk.datastore.shared.incrementingMapWatermarkPrefix
import maryk.datastore.shared.isStoredPropertyDeleteMarker

internal fun rememberIncrementingMapKeys(
    transaction: Transaction,
    families: TableColumnFamilies,
    keyBytes: ByteArray,
    qualifier: ByteArray,
    value: ByteArray,
    valueOffset: Int = 0,
    valueLength: Int = value.size - valueOffset,
) {
    if (isStoredPropertyDeleteMarker(value, valueOffset, valueLength)) return
    val store = transaction.rocksDBDataStore
    for (maximum in incrementingMapKeysForQualifier(store.dataModelsById.getValue(families.modelId), qualifier)) {
        val metadataKey = incrementingMapWatermarkPrefix(families.modelId, keyBytes) + maximum.reference.toStorageByteArray()
        val old = transaction.getForUpdate(store.defaultReadOptions, families.model, metadataKey)
            ?.let { decodeIncrementingMapMaximum(maximum.reference, it) }
        if (old == null || maximum.key > old) transaction.put(families.model, metadataKey, encodeIncrementingMapMaximum(maximum))
    }
}

internal fun readIncrementingMapMaximum(
    dbAccessor: DBAccessor,
    families: TableColumnFamilies,
    keyBytes: ByteArray,
    reference: IncMapReference<out Comparable<Any>, out Any, *>,
): Comparable<Any>? = dbAccessor.get(families.model, dbAccessor.dataStore.defaultReadOptions,
    incrementingMapWatermarkPrefix(families.modelId, keyBytes) + reference.toStorageByteArray())
    ?.let { decodeIncrementingMapMaximum(reference, it) }
