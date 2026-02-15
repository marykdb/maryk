package maryk.datastore.shared.encryption

/**
 * Encrypt/decrypt field payload bytes before/after persistence.
 */
interface FieldEncryptionProvider {
    fun encrypt(value: ByteArray): ByteArray
    fun decrypt(value: ByteArray): ByteArray
}

