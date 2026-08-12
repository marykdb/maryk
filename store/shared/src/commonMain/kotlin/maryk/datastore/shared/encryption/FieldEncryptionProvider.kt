package maryk.datastore.shared.encryption

/**
 * Encrypt/decrypt field payload bytes before/after persistence.
 */
interface FieldEncryptionProvider {
    suspend fun encrypt(value: ByteArray, offset: Int = 0, length: Int = value.size - offset): ByteArray
    suspend fun decrypt(value: ByteArray, offset: Int = 0, length: Int = value.size - offset): ByteArray
}

/**
 * Immutable identity of a persisted sensitive field.
 *
 * Values are included as authenticated additional data by implementations so a
 * ciphertext cannot be moved to another model, record, or property.
 */
class FieldEncryptionContext(
    val modelId: UInt,
    recordKey: ByteArray,
    reference: ByteArray,
) {
    private val storedRecordKey = recordKey.copyOf()
    private val storedReference = reference.copyOf()

    val recordKey: ByteArray
        get() = storedRecordKey.copyOf()

    val reference: ByteArray
        get() = storedReference.copyOf()
}

/**
 * Field encryption provider which authenticates the persisted field identity.
 *
 * Stores require this interface for new sensitive writes. [FieldEncryptionProvider]
 * remains available only to decrypt legacy MKE1 field envelopes.
 */
interface ContextualFieldEncryptionProvider : FieldEncryptionProvider {
    suspend fun encrypt(
        context: FieldEncryptionContext,
        value: ByteArray,
        offset: Int = 0,
        length: Int = value.size - offset,
    ): ByteArray

    suspend fun decrypt(
        context: FieldEncryptionContext,
        value: ByteArray,
        offset: Int = 0,
        length: Int = value.size - offset,
    ): ByteArray
}
