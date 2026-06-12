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
    associatedData: ByteArray? = null,
    private val tokenSizeBytes: Int = 16,
) : FieldEncryptionProvider, SensitiveIndexTokenProvider {
    private val provider = CryptographyProvider.Default
    private val aesGcm = provider.get(AES.GCM)
    private val hmac = provider.get(HMAC)
    private val nonceKeyGenerator = aesGcm.keyGenerator(AES.Key.Size.B128)
    private val encryptionKeyBytes = encryptionKey.copyOf()
    private val tokenKeyBytes = tokenKey.copyOf()
    private val associatedData = associatedData?.copyOf()

    init {
        require(encryptionKeyBytes.size in AES_KEY_SIZE_BYTES) {
            "encryptionKey must be 16, 24, or 32 bytes"
        }
        require(tokenKeyBytes.isNotEmpty()) { "tokenKey cannot be empty" }
        require(tokenSizeBytes in 8..32) { "tokenSizeBytes must be in range 8..32" }
    }

    override suspend fun encrypt(value: ByteArray): ByteArray {
        val aesKey = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, encryptionKeyBytes)
        val aesCipher = aesKey.cipher()
        val nonce = generateNonce()
        val cipherText = aesCipher.encryptWithIv(nonce, value, associatedData)
        return byteArrayOf(V1_PAYLOAD_HEADER) + nonce + cipherText
    }

    override suspend fun decrypt(value: ByteArray): ByteArray {
        require(value.size >= 1 + GCM_NONCE_SIZE_BYTES + GCM_TAG_SIZE_BYTES) {
            "Encrypted payload too short"
        }
        require(value[0] == V1_PAYLOAD_HEADER) {
            "Unsupported encrypted payload version: ${value[0]}"
        }
        val nonceStart = 1
        val nonceEnd = nonceStart + GCM_NONCE_SIZE_BYTES
        val nonce = value.copyOfRange(nonceStart, nonceEnd)
        val cipherText = value.copyOfRange(nonceEnd, value.size)
        val aesKey = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, encryptionKeyBytes)
        val aesCipher = aesKey.cipher()
        return aesCipher.decryptWithIv(nonce, cipherText, associatedData)
    }

    override suspend fun deriveDeterministicToken(modelId: UInt, reference: ByteArray, value: ByteArray): ByteArray {
        val input = byteArrayOf(1) +
            modelId.toByteArray() +
            reference.size.toByteArray() +
            reference +
            value.size.toByteArray() +
            value

        val hmacKey = hmac.keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, tokenKeyBytes)
        val tokenSignatureGenerator = hmacKey.signatureGenerator()
        val fullMac = tokenSignatureGenerator.generateSignature(input)
        return fullMac.copyOf(tokenSizeBytes)
    }

    private suspend fun generateNonce(): ByteArray {
        // Generate cryptographically secure bytes through provider key generation.
        // The raw key bytes are random; first 12 bytes are used as AES-GCM nonce.
        val random = nonceKeyGenerator.generateKey().encodeToByteArray(AES.Key.Format.RAW)
        return random.copyOf(GCM_NONCE_SIZE_BYTES)
    }

    class KeyMaterial(
        encryptionKey: ByteArray,
        tokenKey: ByteArray,
    ) {
        private val _encryptionKey = encryptionKey.copyOf()
        private val _tokenKey = tokenKey.copyOf()

        val encryptionKey: ByteArray
            get() = _encryptionKey.copyOf()

        val tokenKey: ByteArray
            get() = _tokenKey.copyOf()

        operator fun component1(): ByteArray = encryptionKey

        operator fun component2(): ByteArray = tokenKey

        fun copy(
            encryptionKey: ByteArray = this.encryptionKey,
            tokenKey: ByteArray = this.tokenKey,
        ) = KeyMaterial(encryptionKey, tokenKey)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is KeyMaterial) return false
            return _encryptionKey.contentEquals(other._encryptionKey) &&
                _tokenKey.contentEquals(other._tokenKey)
        }

        override fun hashCode(): Int {
            var result = _encryptionKey.contentHashCode()
            result = 31 * result + _tokenKey.contentHashCode()
            return result
        }

        override fun toString() = "KeyMaterial(encryptionKey=***, tokenKey=***)"
    }

    companion object {
        private const val V1_PAYLOAD_HEADER: Byte = 1
        private const val GCM_NONCE_SIZE_BYTES = 12
        private const val GCM_TAG_SIZE_BYTES = 16
        private val AES_KEY_SIZE_BYTES = setOf(16, 24, 32)

        suspend fun generateKeyMaterial(
            encryptionKeySize: BinarySize = AES.Key.Size.B256,
            tokenKeySize: BinarySize = AES.Key.Size.B256,
        ): KeyMaterial {
            val provider = CryptographyProvider.Default
            val aes = provider.get(AES.GCM)
            val hmac = provider.get(HMAC)

            val encryptionKey = aes.keyGenerator(encryptionKeySize)
                .generateKey()
                .encodeToByteArray(AES.Key.Format.RAW)
            val tokenKey = hmac.keyGenerator(SHA256)
                .generateKey()
                .encodeToByteArray(HMAC.Key.Format.RAW)

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
