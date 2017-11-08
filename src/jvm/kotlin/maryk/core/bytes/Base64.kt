package maryk.core.bytes

import java.util.Base64 as JvmBase64

/** Util to convert base 64 */
object Base64 {
    private val base64Decoder by lazy { JvmBase64.getUrlDecoder() }
    private val base64Encoder by lazy { JvmBase64.getUrlEncoder().withoutPadding() }

    /** Get String encoded key as bytes
     * @param base64 to decode
     * @return byte representation
     */
    fun decode(base64: String) = base64Decoder.decode(base64)

    /** Get Bytes as base64 string
     * @param bytes to encode
     * @return Base64 String
     */
    fun encode(bytes: ByteArray) = base64Encoder.encodeToString(bytes)
}