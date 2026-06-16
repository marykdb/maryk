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

    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray {
        validateRange(value, offset, length)
        val aesKey = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, encryptionKeyBytes)
        val aesCipher = aesKey.cipher()
        val nonce = generateNonce()
        val plainText = if (offset == 0 && length == value.size) value else value.copyOfRange(offset, offset + length)
        val cipherText = aesCipher.encryptWithIv(nonce, plainText, associatedData)
        return byteArrayOf(V1_PAYLOAD_HEADER) + nonce + cipherText
    }

    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray {
        validateRange(value, offset, length)
        require(length >= 1 + GCM_NONCE_SIZE_BYTES + GCM_TAG_SIZE_BYTES) {
            "Encrypted payload too short"
        }
        require(value[offset] == V1_PAYLOAD_HEADER) {
            "Unsupported encrypted payload version: ${value[offset]}"
        }
        val nonceStart = offset + 1
        val nonceEnd = nonceStart + GCM_NONCE_SIZE_BYTES
        val nonce = value.copyOfRange(nonceStart, nonceEnd)
        val cipherText = value.copyOfRange(nonceEnd, offset + length)
        val aesKey = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, encryptionKeyBytes)
        val aesCipher = aesKey.cipher()
        return aesCipher.decryptWithIv(nonce, cipherText, associatedData)
    }

    override suspend fun deriveDeterministicToken(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int
    ): ByteArray {
        validateRange(value, offset, length)
        val modelIdBytes = modelId.toByteArray()
        val referenceSizeBytes = reference.size.toByteArray()
        val valueSizeBytes = length.toByteArray()
        val input = ByteArray(1 + modelIdBytes.size + referenceSizeBytes.size + reference.size + valueSizeBytes.size + length)
        var index = 0
        input[index++] = 1
        modelIdBytes.copyInto(input, index)
        index += modelIdBytes.size
        referenceSizeBytes.copyInto(input, index)
        index += referenceSizeBytes.size
        reference.copyInto(input, index)
        index += reference.size
        valueSizeBytes.copyInto(input, index)
        index += valueSizeBytes.size
        value.copyInto(input, index, offset, offset + length)

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

private fun validateRange(value: ByteArray, offset: Int, length: Int) {
    require(offset >= 0) { "Offset cannot be negative: $offset" }
    require(length >= 0) { "Length cannot be negative: $length" }
    require(offset + length <= value.size) { "Range [$offset, ${offset + length}) out of bounds for ${value.size}" }
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
