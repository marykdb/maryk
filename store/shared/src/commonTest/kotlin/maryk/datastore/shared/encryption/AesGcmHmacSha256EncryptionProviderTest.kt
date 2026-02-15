package maryk.datastore.shared.encryption

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AesGcmHmacSha256EncryptionProviderTest {
    @Test
    fun encryptDecryptRoundTrip() {
        val keyMaterial = AesGcmHmacSha256EncryptionProvider.generateKeyMaterial()
        val provider = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = keyMaterial.encryptionKey,
            tokenKey = keyMaterial.tokenKey
        )

        val plain = "super-secret".encodeToByteArray()
        val encrypted = provider.encrypt(plain)
        val decrypted = provider.decrypt(encrypted)

        assertFalse(encrypted.contentEquals(plain))
        assertContentEquals(plain, decrypted)
    }

    @Test
    fun encryptionIsNonceRandomized() {
        val keyMaterial = AesGcmHmacSha256EncryptionProvider.generateKeyMaterial()
        val provider = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = keyMaterial.encryptionKey,
            tokenKey = keyMaterial.tokenKey
        )

        val plain = "same-input".encodeToByteArray()
        val encrypted1 = provider.encrypt(plain)
        val encrypted2 = provider.encrypt(plain)

        assertFalse(encrypted1.contentEquals(encrypted2))
    }

    @Test
    fun deterministicTokenIsStable() {
        val keyMaterial = AesGcmHmacSha256EncryptionProvider.generateKeyMaterial()
        val provider = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = keyMaterial.encryptionKey,
            tokenKey = keyMaterial.tokenKey
        )

        val modelId = 7u
        val reference = byteArrayOf(1, 2, 3)
        val value = "same".encodeToByteArray()

        val token1 = provider.deriveDeterministicToken(modelId, reference, value)
        val token2 = provider.deriveDeterministicToken(modelId, reference, value)

        assertContentEquals(token1, token2)
        assertEquals(16, token1.size)
    }

    @Test
    fun deterministicTokenChangesByInput() {
        val keyMaterial = AesGcmHmacSha256EncryptionProvider.generateKeyMaterial()
        val provider = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = keyMaterial.encryptionKey,
            tokenKey = keyMaterial.tokenKey
        )

        val ref = byteArrayOf(1, 2, 3)
        val tokenA = provider.deriveDeterministicToken(1u, ref, "value".encodeToByteArray())
        val tokenB = provider.deriveDeterministicToken(2u, ref, "value".encodeToByteArray())
        val tokenC = provider.deriveDeterministicToken(1u, byteArrayOf(1, 2, 4), "value".encodeToByteArray())
        val tokenD = provider.deriveDeterministicToken(1u, ref, "value2".encodeToByteArray())

        assertFalse(tokenA.contentEquals(tokenB))
        assertFalse(tokenA.contentEquals(tokenC))
        assertFalse(tokenA.contentEquals(tokenD))
    }

    @Test
    fun rejectsCorruptPayloadHeader() {
        val keyMaterial = AesGcmHmacSha256EncryptionProvider.generateKeyMaterial()
        val provider = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = keyMaterial.encryptionKey,
            tokenKey = keyMaterial.tokenKey
        )

        val encrypted = provider.encrypt("x".encodeToByteArray()).copyOf()
        encrypted[0] = 99

        assertFailsWith<IllegalArgumentException> {
            provider.decrypt(encrypted)
        }
    }
}
