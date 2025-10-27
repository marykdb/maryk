package maryk.datastore.rocksdb.metadata

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.writeBytes
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.RocksDB

private const val MODEL_ID_BYTE_SIZE = UInt.SIZE_BYTES
private const val PREFIXED_ID_SIZE = 1 + MODEL_ID_BYTE_SIZE
internal const val MODEL_NAME_METADATA_PREFIX: Byte = 1

private fun UInt.toMetadataKey(): ByteArray {
    val key = ByteArray(PREFIXED_ID_SIZE)
    key[0] = MODEL_NAME_METADATA_PREFIX
    var index = 1
    this.writeBytes({ key[index++] = it }, length = MODEL_ID_BYTE_SIZE)
    return key
}

private fun ByteArray.toModelIdOrNull(): UInt? {
    if (this.size != PREFIXED_ID_SIZE || this[0] != MODEL_NAME_METADATA_PREFIX) {
        return null
    }
    var index = 1
    return initUInt({ this[index++] })
}

internal fun getStoredModelName(
    rocksDB: RocksDB,
    metadataColumnFamily: ColumnFamilyHandle,
    modelId: UInt,
): String? = rocksDB.get(metadataColumnFamily, modelId.toMetadataKey())?.decodeToString()

internal fun storeModelNameMapping(
    rocksDB: RocksDB,
    metadataColumnFamily: ColumnFamilyHandle,
    modelId: UInt,
    modelName: String,
) {
    val key = modelId.toMetadataKey()
    val existing = rocksDB.get(metadataColumnFamily, key)
    if (existing != null) {
        val existingName = existing.decodeToString()
        if (existingName != modelName) {
            throw StorageException("Model id $modelId is already mapped to $existingName but tried to map it to $modelName")
        }
        return
    }

    rocksDB.put(metadataColumnFamily, key, modelName.encodeToByteArray())
}

internal fun readStoredModelNames(
    rocksDB: RocksDB,
    metadataColumnFamily: ColumnFamilyHandle,
): Map<UInt, String> {
    val stored = mutableMapOf<UInt, String>()

    rocksDB.newIterator(metadataColumnFamily).use { iterator ->
        iterator.seekToFirst()
        while (iterator.isValid()) {
            iterator.key().toModelIdOrNull()?.let { modelId ->
                stored[modelId] = iterator.value().decodeToString()
            }
            iterator.next()
        }
    }

    return stored
}
