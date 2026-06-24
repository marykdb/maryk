package maryk.datastore.foundationdb.processors.helpers

internal inline fun <T> ByteArray.withCurrentPayload(
    noinline decryptValue: DecryptValue?,
    handle: (ByteArray, Int, Int) -> T
): T {
    requireVersionedValue(this)
    return if (decryptValue == null) {
        handle(this, VERSION_BYTE_SIZE, this.size - VERSION_BYTE_SIZE)
    } else {
        val payload = decryptValue(this, VERSION_BYTE_SIZE, this.size - VERSION_BYTE_SIZE)
        handle(payload, 0, payload.size)
    }
}
