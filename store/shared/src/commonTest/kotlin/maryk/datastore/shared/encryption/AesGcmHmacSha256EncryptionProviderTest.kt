package maryk.datastore.shared.encryption

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AesGcmHmacSha256EncryptionProviderTest {
    @Test
    fun encryptDecryptRoundTrip() = runTest {
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
    fun encryptionIsNonceRandomized() = runTest {
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
    fun deterministicTokenIsStable() = runTest {
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
    fun deterministicTokenChangesByInput() = runTest {
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
    fun rejectsCorruptPayloadHeader() = runTest {
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

    @Test
    fun rejectsPayloadWithoutAuthenticationTag() = runTest {
        val keyMaterial = AesGcmHmacSha256EncryptionProvider.generateKeyMaterial()
        val provider = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = keyMaterial.encryptionKey,
            tokenKey = keyMaterial.tokenKey
        )

        assertFailsWith<IllegalArgumentException> {
            provider.decrypt(ByteArray(14).also { it[0] = 1 })
        }
    }

    @Test
    fun rejectsInvalidKeyConfiguration() {
        assertFailsWith<IllegalArgumentException> {
            AesGcmHmacSha256EncryptionProvider(
                encryptionKey = ByteArray(15),
                tokenKey = ByteArray(32)
            )
        }

        assertFailsWith<IllegalArgumentException> {
            AesGcmHmacSha256EncryptionProvider(
                encryptionKey = ByteArray(32),
                tokenKey = ByteArray(0)
            )
        }
    }

    @Test
    fun associatedDataIsDefensivelyCopied() = runTest {
        val keyMaterial = AesGcmHmacSha256EncryptionProvider.generateKeyMaterial()
        val associatedData = byteArrayOf(1, 2, 3)
        val provider = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = keyMaterial.encryptionKey,
            tokenKey = keyMaterial.tokenKey,
            associatedData = associatedData
        )

        val encrypted = provider.encrypt("secret".encodeToByteArray())
        associatedData.fill(9)

        assertContentEquals("secret".encodeToByteArray(), provider.decrypt(encrypted))
    }

    @Test
    fun keyMaterialDefensivelyCopiesKeys() {
        val encryptionKey = ByteArray(32) { it.toByte() }
        val tokenKey = ByteArray(32) { (it + 64).toByte() }

        val keyMaterial = AesGcmHmacSha256EncryptionProvider.KeyMaterial(encryptionKey, tokenKey)
        encryptionKey.fill(0)
        tokenKey.fill(0)

        val exposedEncryptionKey = keyMaterial.encryptionKey
        val exposedTokenKey = keyMaterial.tokenKey
        exposedEncryptionKey.fill(1)
        exposedTokenKey.fill(1)

        assertContentEquals(ByteArray(32) { it.toByte() }, keyMaterial.encryptionKey)
        assertContentEquals(ByteArray(32) { (it + 64).toByte() }, keyMaterial.tokenKey)

        val (componentEncryptionKey, componentTokenKey) = keyMaterial
        componentEncryptionKey.fill(2)
        componentTokenKey.fill(2)

        val copy = keyMaterial.copy()
        copy.encryptionKey.fill(3)
        copy.tokenKey.fill(3)

        assertEquals(keyMaterial, copy)
        assertContentEquals(ByteArray(32) { it.toByte() }, keyMaterial.encryptionKey)
        assertContentEquals(ByteArray(32) { (it + 64).toByte() }, keyMaterial.tokenKey)
    }
}
