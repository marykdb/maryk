package maryk.datastore.foundationdb.metadata

import com.apple.foundationdb.Range
import com.apple.foundationdb.Transaction
import com.apple.foundationdb.TransactionContext
import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.writeBytes
import maryk.datastore.foundationdb.processors.helpers.awaitResult

private const val ID_KEY_SIZE = UInt.SIZE_BYTES

internal fun UInt.toMetadataBytes(): ByteArray {
    val key = ByteArray(ID_KEY_SIZE)
    var index = 0
    this.writeBytes({ key[index++] = it }, length = ID_KEY_SIZE)
    return key
}

private fun decodeModelIdFromKey(key: ByteArray, prefixSize: Int): UInt {
    require(key.size - prefixSize == ID_KEY_SIZE) { "Unexpected key length for metadata entry" }
    var index = prefixSize
    return initUInt({
        key[index++]
    })
}

internal fun ensureModelNameMapping(
    tr: Transaction,
    metadataKey: ByteArray,
    modelName: String,
) {
    val existing = tr.get(metadataKey).awaitResult()
    if (existing != null) {
        val storedName = existing.decodeToString()
        if (storedName != modelName) {
            throw StorageException("Model id is already mapped to $storedName but tried to map it to $modelName")
        }
        return
    }
    tr.set(metadataKey, modelName.encodeToByteArray())
}

internal fun readStoredModelNames(
    tc: TransactionContext,
    metadataPrefix: ByteArray,
): Map<UInt, String> = tc.run { tr ->
    val stored = mutableMapOf<UInt, String>()
    val range = Range.startsWith(metadataPrefix)
    val iterator = tr.getRange(range).iterator()
    while (iterator.hasNext()) {
        val kv = iterator.next()
        stored[decodeModelIdFromKey(kv.key, metadataPrefix.size)] = kv.value.decodeToString()
    }
    stored
}
