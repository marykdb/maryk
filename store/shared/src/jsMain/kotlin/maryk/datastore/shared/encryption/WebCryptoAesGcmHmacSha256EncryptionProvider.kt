@file:Suppress("UnsafeCastFromDynamic")

package maryk.datastore.shared.encryption

import kotlinx.coroutines.await
import kotlin.js.Promise
import kotlin.js.json

/**
 * Browser/JS-native WebCrypto provider.
 *
 * Payload format matches [AesGcmHmacSha256EncryptionProvider]:
 * version byte + 12 byte AES-GCM nonce + cipher text/tag.
 */
class WebCryptoAesGcmHmacSha256EncryptionProvider(
    encryptionKey: ByteArray,
    tokenKey: ByteArray,
    associatedData: ByteArray? = null,
    private val tokenSizeBytes: Int = 16,
) : FieldEncryptionProvider, SensitiveIndexTokenProvider {
    private val encryptionKeyBytes = encryptionKey.copyOf()
    private val tokenKeyBytes = tokenKey.copyOf()
    private val associatedData = associatedData?.copyOf()

    init {
        require(encryptionKeyBytes.size in AES_KEY_SIZE_BYTES) {
            "encryptionKey must be 16, 24, or 32 bytes"
        }
        require(tokenKeyBytes.isNotEmpty()) { "tokenKey cannot be empty" }
        require(tokenSizeBytes in 8..32) { "tokenSizeBytes must be in range 8..32" }
        require(hasWebCrypto()) { "WebCrypto crypto.subtle is not available" }
    }

    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray {
        validateRange(value, offset, length)
        val nonce = randomBytes(GCM_NONCE_SIZE_BYTES)
        val plainText = value.copyOfRange(offset, offset + length)
        val key = importAesKey(encryptionKeyBytes)
        val cipher = subtleEncrypt(key, nonce, plainText, associatedData)
        return byteArrayOf(V1_PAYLOAD_HEADER) + nonce + cipher
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
        val cipher = value.copyOfRange(nonceEnd, offset + length)
        val key = importAesKey(encryptionKeyBytes)
        return subtleDecrypt(key, nonce, cipher, associatedData)
    }

    override suspend fun deriveDeterministicToken(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
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

        val key = importHmacKey(tokenKeyBytes)
        return subtleSign(key, input).copyOf(tokenSizeBytes)
    }
}

private const val V1_PAYLOAD_HEADER: Byte = 1
private const val GCM_NONCE_SIZE_BYTES = 12
private const val GCM_TAG_SIZE_BYTES = 16
private val AES_KEY_SIZE_BYTES = setOf(16, 24, 32)

private fun hasWebCrypto(): Boolean =
    js("!!(globalThis.crypto && globalThis.crypto.subtle && globalThis.crypto.getRandomValues)").unsafeCast<Boolean>()

private fun randomBytes(size: Int): ByteArray {
    val uint8Array = newUint8Array(size)
    js("globalThis.crypto.getRandomValues(uint8Array)")
    return uint8ArrayToByteArray(uint8Array)
}

private suspend fun importAesKey(keyBytes: ByteArray): dynamic =
    cryptoSubtle().importKey(
        "raw",
        keyBytes.toUint8Array(),
        json("name" to "AES-GCM"),
        false,
        arrayOf("encrypt", "decrypt"),
    ).unsafeCast<Promise<dynamic>>().await()

private suspend fun importHmacKey(keyBytes: ByteArray): dynamic =
    cryptoSubtle().importKey(
        "raw",
        keyBytes.toUint8Array(),
        json("name" to "HMAC", "hash" to "SHA-256"),
        false,
        arrayOf("sign"),
    ).unsafeCast<Promise<dynamic>>().await()

private suspend fun subtleEncrypt(
    key: dynamic,
    nonce: ByteArray,
    plainText: ByteArray,
    associatedData: ByteArray?,
): ByteArray {
    val algorithm = if (associatedData == null) {
        json("name" to "AES-GCM", "iv" to nonce.toUint8Array(), "tagLength" to 128)
    } else {
        json("name" to "AES-GCM", "iv" to nonce.toUint8Array(), "additionalData" to associatedData.toUint8Array(), "tagLength" to 128)
    }
    val result = cryptoSubtle().encrypt(algorithm, key, plainText.toUint8Array()).unsafeCast<Promise<dynamic>>().await()
    return arrayBufferToByteArray(result)
}

private suspend fun subtleDecrypt(
    key: dynamic,
    nonce: ByteArray,
    cipherText: ByteArray,
    associatedData: ByteArray?,
): ByteArray {
    val algorithm = if (associatedData == null) {
        json("name" to "AES-GCM", "iv" to nonce.toUint8Array(), "tagLength" to 128)
    } else {
        json("name" to "AES-GCM", "iv" to nonce.toUint8Array(), "additionalData" to associatedData.toUint8Array(), "tagLength" to 128)
    }
    val result = cryptoSubtle().decrypt(algorithm, key, cipherText.toUint8Array()).unsafeCast<Promise<dynamic>>().await()
    return arrayBufferToByteArray(result)
}

private suspend fun subtleSign(key: dynamic, input: ByteArray): ByteArray {
    val result = cryptoSubtle().sign("HMAC", key, input.toUint8Array()).unsafeCast<Promise<dynamic>>().await()
    return arrayBufferToByteArray(result)
}

private fun cryptoSubtle(): dynamic = js("globalThis.crypto.subtle")

private fun ByteArray.toUint8Array(): dynamic {
    val uint8Array = newUint8Array(size)
    for (index in indices) {
        uint8Array[index] = this[index].toInt() and 0xFF
    }
    return uint8Array
}

private fun arrayBufferToByteArray(buffer: dynamic): ByteArray {
    val uint8Array = js("new Uint8Array(buffer)")
    return uint8ArrayToByteArray(uint8Array)
}

private fun newUint8Array(size: Int): dynamic = js("new Uint8Array(size)")

private fun uint8ArrayToByteArray(uint8Array: dynamic): ByteArray {
    val size = uint8Array.length.unsafeCast<Int>()
    return ByteArray(size) { index -> uint8Array[index].unsafeCast<Int>().toByte() }
}

private fun validateRange(value: ByteArray, offset: Int, length: Int) {
    require(offset >= 0) { "offset cannot be negative" }
    require(length >= 0) { "length cannot be negative" }
    require(offset + length <= value.size) { "offset + length exceeds value size" }
}

private fun UInt.toByteArray(): ByteArray = ByteArray(UInt.SIZE_BYTES).also { bytes ->
    val value = toInt()
    bytes[0] = (value ushr 24).toByte()
    bytes[1] = (value ushr 16).toByte()
    bytes[2] = (value ushr 8).toByte()
    bytes[3] = value.toByte()
}

private fun Int.toByteArray(): ByteArray = ByteArray(Int.SIZE_BYTES).also { bytes ->
    bytes[0] = (this ushr 24).toByte()
    bytes[1] = (this ushr 16).toByte()
    bytes[2] = (this ushr 8).toByte()
    bytes[3] = this.toByte()
}
