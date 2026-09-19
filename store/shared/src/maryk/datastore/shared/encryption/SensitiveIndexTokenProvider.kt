package maryk.datastore.shared.encryption

/**
 * Deterministic one-way token derivation for sensitive unique/lookups.
 * Implementations should use a keyed function.
 */
interface SensitiveIndexTokenProvider {
    suspend fun deriveDeterministicToken(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int = 0,
        length: Int = value.size - offset
    ): ByteArray

    /**
     * Tokens accepted while rotating deterministic index keys.
     * Existing providers keep returning one token.
     */
    suspend fun deriveDeterministicTokenCandidates(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int = 0,
        length: Int = value.size - offset,
    ): List<ByteArray> = listOf(deriveDeterministicToken(modelId, reference, value, offset, length))
}
