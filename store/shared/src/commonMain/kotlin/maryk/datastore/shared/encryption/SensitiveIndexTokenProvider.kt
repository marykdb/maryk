package maryk.datastore.shared.encryption

/**
 * Deterministic one-way token derivation for sensitive unique/lookups.
 * Implementations should use a keyed function.
 */
interface SensitiveIndexTokenProvider {
    suspend fun deriveDeterministicToken(modelId: UInt, reference: ByteArray, value: ByteArray): ByteArray
}
