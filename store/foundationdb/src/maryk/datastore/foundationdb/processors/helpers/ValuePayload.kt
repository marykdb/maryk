package maryk.datastore.foundationdb.processors.helpers

internal inline fun <T> ByteArray.withCurrentPayload(
    noinline decryptValue: DecryptValue?,
    modelId: UInt,
    keyBytes: ByteArray,
    referenceBytes: ByteArray,
    handle: (ByteArray, Int, Int) -> T
): T {
    requireVersionedValue(this)
    return if (decryptValue == null) {
        handle(this, VERSION_BYTE_SIZE, this.size - VERSION_BYTE_SIZE)
    } else {
        val payload = decryptValue(modelId, this, VERSION_BYTE_SIZE, this.size - VERSION_BYTE_SIZE, keyBytes, referenceBytes)
        handle(payload, 0, payload.size)
    }
}
