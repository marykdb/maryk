package maryk.datastore.shared.encryption

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyringFieldEncryptionProviderTest {
    @Test
    fun readsLegacyAndPreviousKeysAndWritesActiveKey() = runTest {
        val old = XorProvider(1)
        val current = XorProvider(2)
        val keyring = KeyringFieldEncryptionProvider(
            activeKeyId = "current",
            providers = mapOf("old" to old, "current" to current),
            legacyProvider = old,
        )
        val plain = "secret".encodeToByteArray()
        val legacy = old.encrypt(plain)
        val oldEnvelope = KeyringFieldEncryptionProvider(
            activeKeyId = "old",
            providers = mapOf("old" to old),
        ).encrypt(plain)
        val currentEnvelope = keyring.encrypt(plain)

        assertContentEquals(plain, keyring.decrypt(legacy))
        assertContentEquals(plain, keyring.decrypt(oldEnvelope))
        assertContentEquals(plain, keyring.decrypt(currentEnvelope))
        assertTrue(keyring.needsReEncryption(legacy))
        assertTrue(keyring.needsReEncryption(oldEnvelope))
        assertFalse(keyring.needsReEncryption(currentEnvelope))
        assertEquals("current", keyring.keyId(currentEnvelope))
        val tokenCandidates = keyring.deriveDeterministicTokenCandidates(1u, byteArrayOf(1), plain)
        assertEquals(2, tokenCandidates.size)
        assertContentEquals(byteArrayOf(2), tokenCandidates.first())
    }

    @Test
    fun legacyPayloadStartingWithVersionByteIsNotMisclassified() = runTest {
        val legacy = XorProvider(1)
        val keyring = KeyringFieldEncryptionProvider(
            activeKeyId = "current",
            providers = mapOf("current" to XorProvider(2)),
            legacyProvider = legacy,
        )
        val plain = byteArrayOf(3, 9, 12)
        val legacyPayload = legacy.encrypt(plain)

        assertEquals(2.toByte(), legacyPayload.first())
        assertContentEquals(plain, keyring.decrypt(legacyPayload))
        assertContentEquals(byteArrayOf(), keyring.decrypt(byteArrayOf()))
    }

    @Test
    fun reEncryptionStateResumesByCursor() = runTest {
        val old = XorProvider(1)
        val current = XorProvider(2)
        val oldEnvelope = KeyringFieldEncryptionProvider("old", mapOf("old" to old))
            .encrypt("secret".encodeToByteArray())
        val keyring = KeyringFieldEncryptionProvider(
            activeKeyId = "current",
            providers = mapOf("old" to old, "current" to current),
        )
        var written: ByteArray? = null
        var persisted: ReEncryptionState? = null
        val result = runReEncryptionBatch(
            provider = keyring,
            state = ReEncryptionState(targetKeyId = "current"),
            read = { ReEncryptionBatch(listOf(EncryptedFieldRecord(byteArrayOf(1), oldEnvelope)), null) },
            write = { _, payload -> written = payload },
            persistState = { persisted = it },
        )

        assertEquals(ReEncryptionStatus.Completed, result.status)
        assertEquals(1uL, result.processed)
        assertEquals(result, persisted)
        assertContentEquals("secret".encodeToByteArray(), keyring.decrypt(requireNotNull(written)))
    }
}

private class XorProvider(private val mask: Byte) : FieldEncryptionProvider, SensitiveIndexTokenProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray =
        value.copyOfRange(offset, offset + length).also { bytes ->
            bytes.indices.forEach { bytes[it] = (bytes[it].toInt() xor mask.toInt()).toByte() }
        }

    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray =
        encrypt(value, offset, length)

    override suspend fun deriveDeterministicToken(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray = byteArrayOf(mask)
}
