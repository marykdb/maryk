package maryk.datastore.shared.encryption

import dev.whyoleg.cryptography.BinarySize
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * Cross-platform field encryption provider:
 * - AES-GCM (256-bit by default) for payload encryption
 * - HMAC-SHA256 for deterministic sensitive lookup tokens
 *
 * Runtime provider comes from `CryptographyProvider.Default`:
 * - JVM: JDK crypto
 * - Apple native: Apple provider
 * - Linux native: OpenSSL provider
 */
@OptIn(DelicateCryptographyApi::class)
class AesGcmHmacSha256EncryptionProvider(
    encryptionKey: ByteArray,
    tokenKey: ByteArray,
    private val associatedData: ByteArray? = null,
    private val tokenSizeBytes: Int = 16,
) : FieldEncryptionProvider, SensitiveIndexTokenProvider {
    private val provider = CryptographyProvider.Default
    private val aesGcm = provider.get(AES.GCM)
    private val aesKey = aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, encryptionKey)
    private val aesCipher = aesKey.cipher()
    private val nonceKeyGenerator = aesGcm.keyGenerator(AES.Key.Size.B128)

    private val hmac = provider.get(HMAC)
    private val hmacKey = hmac.keyDecoder(SHA256).decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, tokenKey)
    private val tokenSignatureGenerator = hmacKey.signatureGenerator()

    init {
        require(tokenSizeBytes in 8..32) { "tokenSizeBytes must be in range 8..32" }
    }

    override fun encrypt(value: ByteArray): ByteArray {
        val nonce = generateNonce()
        val cipherText = aesCipher.encryptWithIvBlocking(nonce, value, associatedData)
        return byteArrayOf(V1_PAYLOAD_HEADER) + nonce + cipherText
    }

    override fun decrypt(value: ByteArray): ByteArray {
        require(value.size > 1 + GCM_NONCE_SIZE_BYTES) {
            "Encrypted payload too short"
        }
        require(value[0] == V1_PAYLOAD_HEADER) {
            "Unsupported encrypted payload version: ${value[0]}"
        }
        val nonceStart = 1
        val nonceEnd = nonceStart + GCM_NONCE_SIZE_BYTES
        val nonce = value.copyOfRange(nonceStart, nonceEnd)
        val cipherText = value.copyOfRange(nonceEnd, value.size)
        return aesCipher.decryptWithIvBlocking(nonce, cipherText, associatedData)
    }

    override fun deriveDeterministicToken(modelId: UInt, reference: ByteArray, value: ByteArray): ByteArray {
        val input = byteArrayOf(1) +
            modelId.toByteArray() +
            reference.size.toByteArray() +
            reference +
            value.size.toByteArray() +
            value

        val fullMac = tokenSignatureGenerator.generateSignatureBlocking(input)
        return fullMac.copyOf(tokenSizeBytes)
    }

    private fun generateNonce(): ByteArray {
        // Generate cryptographically secure bytes through provider key generation.
        // The raw key bytes are random; first 12 bytes are used as AES-GCM nonce.
        val random = nonceKeyGenerator.generateKeyBlocking().encodeToByteArrayBlocking(AES.Key.Format.RAW)
        return random.copyOf(GCM_NONCE_SIZE_BYTES)
    }

    data class KeyMaterial(
        val encryptionKey: ByteArray,
        val tokenKey: ByteArray,
    )

    companion object {
        private const val V1_PAYLOAD_HEADER: Byte = 1
        private const val GCM_NONCE_SIZE_BYTES = 12

        fun generateKeyMaterial(
            encryptionKeySize: BinarySize = AES.Key.Size.B256,
            tokenKeySize: BinarySize = AES.Key.Size.B256,
        ): KeyMaterial {
            val provider = CryptographyProvider.Default
            val aes = provider.get(AES.GCM)
            val hmac = provider.get(HMAC)

            val encryptionKey = aes.keyGenerator(encryptionKeySize)
                .generateKeyBlocking()
                .encodeToByteArrayBlocking(AES.Key.Format.RAW)
            val tokenKey = hmac.keyGenerator(SHA256)
                .generateKeyBlocking()
                .encodeToByteArrayBlocking(HMAC.Key.Format.RAW)

            val requestedTokenKeyBytes = tokenKeySize.inBytes
            require(requestedTokenKeyBytes <= tokenKey.size) {
                "Requested token key size ($requestedTokenKeyBytes bytes) exceeds provider key size (${tokenKey.size} bytes)"
            }
            val sizedTokenKey = if (requestedTokenKeyBytes == tokenKey.size) {
                tokenKey
            } else {
                tokenKey.copyOf(requestedTokenKeyBytes)
            }

            return KeyMaterial(
                encryptionKey = encryptionKey,
                tokenKey = sizedTokenKey,
            )
        }
    }
}

private fun UInt.toByteArray(): ByteArray = ByteArray(UInt.SIZE_BYTES).also { bytes ->
    bytes[0] = (this shr 24).toByte()
    bytes[1] = (this shr 16).toByte()
    bytes[2] = (this shr 8).toByte()
    bytes[3] = this.toByte()
}

private fun Int.toByteArray(): ByteArray = ByteArray(Int.SIZE_BYTES).also { bytes ->
    bytes[0] = (this shr 24).toByte()
    bytes[1] = (this shr 16).toByte()
    bytes[2] = (this shr 8).toByte()
    bytes[3] = this.toByte()
}
