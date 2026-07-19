package maryk.datastore.shared.encryption

/**
 * Versioned keyring envelope.
 *
 * New ciphertexts contain the active key id. Legacy provider payloads remain
 * readable through [legacyProvider], allowing an additive rollout.
 */
class KeyringFieldEncryptionProvider(
    val activeKeyId: String,
    providers: Map<String, FieldEncryptionProvider>,
    private val legacyProvider: FieldEncryptionProvider? = providers[activeKeyId],
    private val tokenReadKeyIds: List<String> = providers.keys.toList(),
) : FieldEncryptionProvider, SensitiveIndexTokenProvider {
    private val providers = providers.toMap()

    init {
        require(activeKeyId.encodeToByteArray().size in 1..MAX_KEY_ID_BYTES) {
            "activeKeyId must encode to 1..$MAX_KEY_ID_BYTES bytes"
        }
        require(this.providers.containsKey(activeKeyId)) { "No provider registered for active key `$activeKeyId`" }
        require(tokenReadKeyIds.isNotEmpty()) { "tokenReadKeyIds cannot be empty" }
        require(tokenReadKeyIds.all(this.providers::containsKey)) { "Every token read key must be registered" }
    }

    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray {
        val keyId = activeKeyId.encodeToByteArray()
        val encrypted = providers.getValue(activeKeyId).encrypt(value, offset, length)
        return ByteArray(KEYRING_HEADER_SIZE + keyId.size + encrypted.size).also { result ->
            KEYRING_MAGIC.copyInto(result)
            result[KEYRING_MAGIC.size] = KEYRING_PAYLOAD_VERSION
            result[KEYRING_MAGIC.size + 1] = keyId.size.toByte()
            keyId.copyInto(result, KEYRING_HEADER_SIZE)
            encrypted.copyInto(result, KEYRING_HEADER_SIZE + keyId.size)
        }
    }

    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray {
        validateRange(value, offset, length)
        if (!hasKeyringMagic(value, offset, length)) {
            return requireNotNull(legacyProvider) {
                "Legacy encrypted payload encountered without a legacy provider"
            }.decrypt(value, offset, length)
        }
        require(length >= KEYRING_HEADER_SIZE + 1) { "Keyring encrypted payload too short" }
        val version = value[offset + KEYRING_MAGIC.size]
        require(version == KEYRING_PAYLOAD_VERSION) {
            "Unsupported keyring encrypted payload version ${version.toUByte()}"
        }
        val keyIdSize = value[offset + KEYRING_MAGIC.size + 1].toUByte().toInt()
        require(keyIdSize in 1..MAX_KEY_ID_BYTES && length > KEYRING_HEADER_SIZE + keyIdSize) {
            "Invalid encrypted payload key id"
        }
        val payloadOffset = offset + KEYRING_HEADER_SIZE + keyIdSize
        val keyId = value.copyOfRange(offset + KEYRING_HEADER_SIZE, payloadOffset).decodeToString()
        val provider = providers[keyId]
            ?: throw IllegalArgumentException("Encrypted payload uses unavailable key `$keyId`")
        return provider.decrypt(value, payloadOffset, length - KEYRING_HEADER_SIZE - keyIdSize)
    }

    fun keyId(value: ByteArray, offset: Int = 0, length: Int = value.size - offset): String? {
        validateRange(value, offset, length)
        if (!hasKeyringMagic(value, offset, length) || length < KEYRING_HEADER_SIZE + 1) return null
        if (value[offset + KEYRING_MAGIC.size] != KEYRING_PAYLOAD_VERSION) return null
        val keyIdSize = value[offset + KEYRING_MAGIC.size + 1].toUByte().toInt()
        if (keyIdSize !in 1..MAX_KEY_ID_BYTES || length <= KEYRING_HEADER_SIZE + keyIdSize) return null
        return value.copyOfRange(
            offset + KEYRING_HEADER_SIZE,
            offset + KEYRING_HEADER_SIZE + keyIdSize,
        ).decodeToString()
    }

    fun needsReEncryption(value: ByteArray, offset: Int = 0, length: Int = value.size - offset): Boolean =
        keyId(value, offset, length) != activeKeyId

    override suspend fun deriveDeterministicToken(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray = activeTokenProvider().deriveDeterministicToken(modelId, reference, value, offset, length)

    override suspend fun deriveDeterministicTokenCandidates(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): List<ByteArray> = buildList {
        (listOf(activeKeyId) + tokenReadKeyIds).distinct().forEach { keyId ->
            val provider = providers.getValue(keyId) as? SensitiveIndexTokenProvider
                ?: throw IllegalStateException("Provider for token key `$keyId` does not derive deterministic tokens")
            val token = provider.deriveDeterministicToken(modelId, reference, value, offset, length)
            if (none(token::contentEquals)) add(token)
        }
    }

    private fun activeTokenProvider(): SensitiveIndexTokenProvider =
        providers.getValue(activeKeyId) as? SensitiveIndexTokenProvider
            ?: throw IllegalStateException("Active provider does not derive deterministic tokens")

    private fun validateRange(value: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= value.size - length) { "Invalid value range" }
    }

    private fun hasKeyringMagic(value: ByteArray, offset: Int, length: Int): Boolean =
        length >= KEYRING_MAGIC.size &&
            KEYRING_MAGIC.indices.all { index -> value[offset + index] == KEYRING_MAGIC[index] }

    private companion object {
        const val MAX_KEY_ID_BYTES = 255
        const val KEYRING_PAYLOAD_VERSION: Byte = 2
        val KEYRING_MAGIC = byteArrayOf(0x4d, 0x4b, 0x45, 0x4b) // "MKEK"
        val KEYRING_HEADER_SIZE = KEYRING_MAGIC.size + 2
    }
}
