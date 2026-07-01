@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package maryk.datastore.shared.encryption

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.js
import kotlin.js.toInt
import kotlin.js.toJsNumber
import kotlin.js.unsafeCast

/**
 * Browser/WasmJS-native WebCrypto provider.
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
        val cipher = subtleEncrypt(encryptionKeyBytes, nonce, plainText, associatedData)
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
        return subtleDecrypt(encryptionKeyBytes, nonce, cipher, associatedData)
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

        return subtleSign(tokenKeyBytes, input).copyOf(tokenSizeBytes)
    }
}

private const val V1_PAYLOAD_HEADER: Byte = 1
private const val GCM_NONCE_SIZE_BYTES = 12
private const val GCM_TAG_SIZE_BYTES = 16
private val AES_KEY_SIZE_BYTES = setOf(16, 24, 32)

private fun hasWebCrypto(): Boolean =
    js("!!(globalThis.crypto && globalThis.crypto.subtle && globalThis.crypto.getRandomValues)")

private fun randomBytes(size: Int): ByteArray =
    webCryptoRandomBytes(size).toByteArray()

private suspend fun subtleEncrypt(
    key: ByteArray,
    nonce: ByteArray,
    plainText: ByteArray,
    associatedData: ByteArray?,
): ByteArray = suspendCancellableCoroutine { continuation ->
    webCryptoEncrypt(
        key = key.toJsBytes(),
        nonce = nonce.toJsBytes(),
        plainText = plainText.toJsBytes(),
        associatedData = associatedData?.toJsBytes(),
        onSuccess = { continuation.resume(it.toByteArray()) },
        onError = { continuation.resumeWithException(IllegalStateException(it)) },
    )
}

private suspend fun subtleDecrypt(
    key: ByteArray,
    nonce: ByteArray,
    cipherText: ByteArray,
    associatedData: ByteArray?,
): ByteArray = suspendCancellableCoroutine { continuation ->
    webCryptoDecrypt(
        key = key.toJsBytes(),
        nonce = nonce.toJsBytes(),
        cipherText = cipherText.toJsBytes(),
        associatedData = associatedData?.toJsBytes(),
        onSuccess = { continuation.resume(it.toByteArray()) },
        onError = { continuation.resumeWithException(IllegalStateException(it)) },
    )
}

private suspend fun subtleSign(key: ByteArray, input: ByteArray): ByteArray =
    suspendCancellableCoroutine { continuation ->
        webCryptoSign(
            key = key.toJsBytes(),
            input = input.toJsBytes(),
            onSuccess = { continuation.resume(it.toByteArray()) },
            onError = { continuation.resumeWithException(IllegalStateException(it)) },
        )
    }

private fun ByteArray.toJsBytes(): JsArray<JsNumber> =
    JsArray<JsNumber>().also { array ->
        forEachIndexed { index, byte ->
            array[index] = byte.toInt().toJsNumber()
        }
    }

private fun JsAny.toByteArray(): ByteArray {
    val array = unsafeCast<JsArray<JsNumber>>()
    return ByteArray(array.length) { index -> array[index]!!.toInt().toByte() }
}

private fun webCryptoRandomBytes(size: Int): JsArray<JsNumber> = js(
    """
    (() => {
    const bytes = new Uint8Array(size);
    globalThis.crypto.getRandomValues(bytes);
    return Array.from(bytes, (byte) => byte > 127 ? byte - 256 : byte);
    })()
    """
)

private fun webCryptoEncrypt(
    key: JsArray<JsNumber>,
    nonce: JsArray<JsNumber>,
    plainText: JsArray<JsNumber>,
    associatedData: JsArray<JsNumber>?,
    onSuccess: (JsAny) -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        (async () => {
            try {
                const toUint8Array = (array) => Uint8Array.from(Array.from(array, (byte) => byte & 255));
                const cryptoKey = await globalThis.crypto.subtle.importKey(
                    "raw",
                    toUint8Array(key),
                    { name: "AES-GCM" },
                    false,
                    ["encrypt"]
                );
                const algorithm = {
                    name: "AES-GCM",
                    iv: toUint8Array(nonce),
                    tagLength: 128
                };
                if (associatedData != null) {
                    algorithm.additionalData = toUint8Array(associatedData);
                }
                const encrypted = new Uint8Array(await globalThis.crypto.subtle.encrypt(
                    algorithm,
                    cryptoKey,
                    toUint8Array(plainText)
                ));
                onSuccess(Array.from(encrypted, (byte) => byte > 127 ? byte - 256 : byte));
            } catch (error) {
                onError(error && error.message ? error.message : String(error));
            }
        })();
        """
    )
}

private fun webCryptoDecrypt(
    key: JsArray<JsNumber>,
    nonce: JsArray<JsNumber>,
    cipherText: JsArray<JsNumber>,
    associatedData: JsArray<JsNumber>?,
    onSuccess: (JsAny) -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        (async () => {
            try {
                const toUint8Array = (array) => Uint8Array.from(Array.from(array, (byte) => byte & 255));
                const cryptoKey = await globalThis.crypto.subtle.importKey(
                    "raw",
                    toUint8Array(key),
                    { name: "AES-GCM" },
                    false,
                    ["decrypt"]
                );
                const algorithm = {
                    name: "AES-GCM",
                    iv: toUint8Array(nonce),
                    tagLength: 128
                };
                if (associatedData != null) {
                    algorithm.additionalData = toUint8Array(associatedData);
                }
                const decrypted = new Uint8Array(await globalThis.crypto.subtle.decrypt(
                    algorithm,
                    cryptoKey,
                    toUint8Array(cipherText)
                ));
                onSuccess(Array.from(decrypted, (byte) => byte > 127 ? byte - 256 : byte));
            } catch (error) {
                onError(error && error.message ? error.message : String(error));
            }
        })();
        """
    )
}

private fun webCryptoSign(
    key: JsArray<JsNumber>,
    input: JsArray<JsNumber>,
    onSuccess: (JsAny) -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        (async () => {
            try {
                const toUint8Array = (array) => Uint8Array.from(Array.from(array, (byte) => byte & 255));
                const cryptoKey = await globalThis.crypto.subtle.importKey(
                    "raw",
                    toUint8Array(key),
                    { name: "HMAC", hash: "SHA-256" },
                    false,
                    ["sign"]
                );
                const signature = new Uint8Array(await globalThis.crypto.subtle.sign(
                    "HMAC",
                    cryptoKey,
                    toUint8Array(input)
                ));
                onSuccess(Array.from(signature, (byte) => byte > 127 ? byte - 256 : byte));
            } catch (error) {
                onError(error && error.message ? error.message : String(error));
            }
        })();
        """
    )
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
