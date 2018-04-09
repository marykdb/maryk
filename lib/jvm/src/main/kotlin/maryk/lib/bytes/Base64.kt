package maryk.lib.bytes

import java.util.Base64 as JvmBase64

/** Util to convert base 64 */
actual object Base64 {
    private val base64Decoder by lazy { JvmBase64.getDecoder() }
    private val base64Encoder by lazy { JvmBase64.getEncoder().withoutPadding() }

    /** Decode [base64] string into bytes array */
    actual fun decode(base64: String) = base64Decoder.decode(base64)

    /** Encode [bytes] array into a base64 String */
    actual fun encode(bytes: ByteArray): String = base64Encoder.encodeToString(bytes)
}
