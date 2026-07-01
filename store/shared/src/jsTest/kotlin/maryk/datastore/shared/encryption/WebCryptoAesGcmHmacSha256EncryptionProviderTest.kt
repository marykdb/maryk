package maryk.datastore.shared.encryption

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebCryptoAesGcmHmacSha256EncryptionProviderTest {
    private val encryptionKey = ByteArray(32) { index -> (index + 1).toByte() }
    private val tokenKey = ByteArray(32) { index -> (index + 41).toByte() }
    private val associatedData = "maryk-webcrypto-test".encodeToByteArray()

    @Test
    fun encryptsAndDecryptsWithWebCrypto() = runTest {
        val provider = WebCryptoAesGcmHmacSha256EncryptionProvider(
            encryptionKey = encryptionKey,
            tokenKey = tokenKey,
            associatedData = associatedData,
        )
        val plainText = "sensitive-browser-value".encodeToByteArray()

        val encrypted = provider.encrypt(plainText, 0, plainText.size)
        val decrypted = provider.decrypt(encrypted, 0, encrypted.size)

        assertFalse(encrypted.contentEquals(plainText))
        assertContentEquals(plainText, decrypted)
    }

    @Test
    fun usesFreshNonceForEachEncryption() = runTest {
        val provider = WebCryptoAesGcmHmacSha256EncryptionProvider(
            encryptionKey = encryptionKey,
            tokenKey = tokenKey,
        )
        val plainText = "same-value".encodeToByteArray()

        val first = provider.encrypt(plainText, 0, plainText.size)
        val second = provider.encrypt(plainText, 0, plainText.size)

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun derivesDeterministicTokenCompatibleWithCommonProvider() = runTest {
        val webCryptoProvider = WebCryptoAesGcmHmacSha256EncryptionProvider(
            encryptionKey = encryptionKey,
            tokenKey = tokenKey,
            tokenSizeBytes = 20,
        )
        val commonProvider = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = encryptionKey,
            tokenKey = tokenKey,
            tokenSizeBytes = 20,
        )
        val reference = byteArrayOf(1, 2, 3)
        val value = "lookup-value".encodeToByteArray()

        val first = webCryptoProvider.deriveDeterministicToken(987u, reference, value, 0, value.size)
        val second = webCryptoProvider.deriveDeterministicToken(987u, reference, value, 0, value.size)
        val expected = commonProvider.deriveDeterministicToken(987u, reference, value, 0, value.size)

        assertTrue(first.contentEquals(second))
        assertContentEquals(expected, first)
    }

    @Test
    fun payloadsAreCompatibleWithCommonProvider() = runTest {
        val webCryptoProvider = WebCryptoAesGcmHmacSha256EncryptionProvider(
            encryptionKey = encryptionKey,
            tokenKey = tokenKey,
            associatedData = associatedData,
        )
        val commonProvider = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = encryptionKey,
            tokenKey = tokenKey,
            associatedData = associatedData,
        )
        val plainText = ByteArray(64) { index -> (index * 7 - 128).toByte() }

        val commonPayload = commonProvider.encrypt(plainText, 3, 41)
        val webCryptoPayload = webCryptoProvider.encrypt(plainText, 3, 41)

        assertContentEquals(plainText.copyOfRange(3, 44), webCryptoProvider.decrypt(commonPayload, 0, commonPayload.size))
        assertContentEquals(plainText.copyOfRange(3, 44), commonProvider.decrypt(webCryptoPayload, 0, webCryptoPayload.size))
    }
}
