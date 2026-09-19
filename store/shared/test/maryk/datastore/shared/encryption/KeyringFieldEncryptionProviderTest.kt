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
    fun readsUnwrappedLegacyContextualCiphertext() = runTest {
        val legacy = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = ByteArray(32) { 1 },
            tokenKey = ByteArray(32) { 2 },
        )
        val current = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = ByteArray(32) { 3 },
            tokenKey = ByteArray(32) { 4 },
        )
        val keyring = KeyringFieldEncryptionProvider(
            activeKeyId = "current",
            providers = mapOf("current" to current),
            legacyProvider = legacy,
        )
        val context = FieldEncryptionContext(1u, byteArrayOf(2), byteArrayOf(3))
        val plain = "legacy contextual".encodeToByteArray()
        val unwrappedCiphertext = legacy.encrypt(context, plain)

        assertContentEquals(plain, keyring.decrypt(context, unwrappedCiphertext))
        assertTrue(keyring.needsReEncryption(unwrappedCiphertext))
    }

    @Test
    fun reEncryptionStateResumesByCursor() = runTest {
        val old = XorProvider(1)
        val current = XorProvider(2)
        val oldEnvelope = KeyringFieldEncryptionProvider("old", mapOf("old" to old))
            .encrypt("secret".encodeToByteArray())
        val persistedOldEnvelope = FieldEncryptionEnvelope.Legacy.magic + oldEnvelope
        val keyring = KeyringFieldEncryptionProvider(
            activeKeyId = "current",
            providers = mapOf("old" to old, "current" to current),
        )
        var written: ByteArray? = null
        var persisted: ReEncryptionState? = null
        val result = runReEncryptionBatch(
            provider = keyring,
            state = ReEncryptionState(targetKeyId = "current"),
            read = {
                ReEncryptionBatch(
                    listOf(
                        EncryptedFieldRecord(
                            id = byteArrayOf(1),
                            modelId = 1u,
                            recordKey = byteArrayOf(2),
                            reference = byteArrayOf(3),
                            payload = persistedOldEnvelope,
                        )
                    ),
                    null,
                )
            },
            write = { _, payload -> written = payload },
            persistState = { persisted = it },
        )

        assertEquals(ReEncryptionStatus.Completed, result.status)
        assertEquals(1uL, result.processed)
        assertEquals(result, persisted)
        val rotated = requireNotNull(written)
        val envelopeSize = FieldEncryptionEnvelope.Contextual.magic.size
        assertContentEquals(
            "secret".encodeToByteArray(),
            keyring.decrypt(
                FieldEncryptionContext(1u, byteArrayOf(2), byteArrayOf(3)),
                rotated,
                envelopeSize,
                rotated.size - envelopeSize,
            ),
        )
    }

    @Test
    fun reEncryptionRotatesContextualPersistedFieldEnvelope() = runTest {
        val oldProvider = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = ByteArray(32) { 1 },
            tokenKey = ByteArray(32) { 2 },
        )
        val currentProvider = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = ByteArray(32) { 3 },
            tokenKey = ByteArray(32) { 4 },
        )
        val oldKeyring = KeyringFieldEncryptionProvider("old", mapOf("old" to oldProvider))
        val keyring = KeyringFieldEncryptionProvider(
            activeKeyId = "current",
            providers = mapOf("old" to oldProvider, "current" to currentProvider),
        )
        val modelId = 42u
        val recordKey = byteArrayOf(1, 2, 3)
        val reference = byteArrayOf(4, 5)
        val context = FieldEncryptionContext(modelId, recordKey, reference)
        val plain = "persisted secret".encodeToByteArray()
        val oldPersistedPayload = FieldEncryptionEnvelope.Contextual.magic + oldKeyring.encrypt(context, plain)
        var written: ByteArray? = null

        runReEncryptionBatch(
            provider = keyring,
            state = ReEncryptionState(targetKeyId = "current"),
            read = {
                ReEncryptionBatch(
                    records = listOf(
                        EncryptedFieldRecord(
                            id = byteArrayOf(9),
                            modelId = modelId,
                            recordKey = recordKey,
                            reference = reference,
                            payload = oldPersistedPayload,
                        )
                    ),
                    nextCursor = null,
                )
            },
            write = { _, payload -> written = payload },
            persistState = {},
        )

        val rotated = requireNotNull(written)
        assertEquals(FieldEncryptionEnvelope.Contextual, FieldEncryptionEnvelope.fromSensitiveValue(rotated))
        val envelopeSize = FieldEncryptionEnvelope.Contextual.magic.size
        assertEquals("current", keyring.keyId(rotated, envelopeSize, rotated.size - envelopeSize))
        assertContentEquals(
            plain,
            keyring.decrypt(context, rotated, envelopeSize, rotated.size - envelopeSize),
        )
    }
}

private class XorProvider(private val mask: Byte) : ContextualFieldEncryptionProvider, SensitiveIndexTokenProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray =
        value.copyOfRange(offset, offset + length).also { bytes ->
            bytes.indices.forEach { bytes[it] = (bytes[it].toInt() xor mask.toInt()).toByte() }
        }

    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray =
        encrypt(value, offset, length)

    override suspend fun encrypt(
        context: FieldEncryptionContext,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray = encrypt(value, offset, length)

    override suspend fun decrypt(
        context: FieldEncryptionContext,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray = decrypt(value, offset, length)

    override suspend fun deriveDeterministicToken(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray = byteArrayOf(mask)
}
