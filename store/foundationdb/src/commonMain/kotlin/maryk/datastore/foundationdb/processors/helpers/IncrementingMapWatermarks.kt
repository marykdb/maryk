package maryk.datastore.foundationdb.processors.helpers

import maryk.core.properties.references.IncMapReference
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.shared.decodeIncrementingMapMaximum
import maryk.datastore.shared.encodeIncrementingMapMaximum
import maryk.datastore.shared.incrementingMapKeysForQualifier
import maryk.datastore.shared.incrementingMapWatermarkPrefix
import maryk.datastore.shared.isStoredPropertyDeleteMarker
import maryk.foundationdb.Transaction

internal fun rememberIncrementingMapKeys(
    tr: Transaction,
    directories: IsTableDirectories,
    keyBytes: ByteArray,
    qualifier: ByteArray,
    value: ByteArray,
) {
    if (isStoredPropertyDeleteMarker(value)) return
    for (maximum in incrementingMapKeysForQualifier(directories.dataStore.dataModelsById.getValue(directories.modelId), qualifier)) {
        val metadataKey = packKey(directories.modelPrefix,
            incrementingMapWatermarkPrefix(directories.modelId, keyBytes), maximum.reference.toStorageByteArray())
        val old = tr.get(metadataKey).awaitResult()?.let { decodeIncrementingMapMaximum(maximum.reference, it) }
        if (old == null || maximum.key > old) tr.set(metadataKey, encodeIncrementingMapMaximum(maximum))
    }
}

internal fun readIncrementingMapMaximum(
    tr: Transaction,
    directories: IsTableDirectories,
    keyBytes: ByteArray,
    reference: IncMapReference<out Comparable<Any>, out Any, *>,
): Comparable<Any>? = tr.get(packKey(directories.modelPrefix,
    incrementingMapWatermarkPrefix(directories.modelId, keyBytes), reference.toStorageByteArray())).awaitResult()
    ?.let { decodeIncrementingMapMaximum(reference, it) }
