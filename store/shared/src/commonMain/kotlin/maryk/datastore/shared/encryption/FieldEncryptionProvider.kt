package maryk.datastore.shared.encryption

/**
 * Encrypt/decrypt field payload bytes before/after persistence.
 */
interface FieldEncryptionProvider {
    suspend fun encrypt(value: ByteArray): ByteArray
    suspend fun decrypt(value: ByteArray): ByteArray
}
