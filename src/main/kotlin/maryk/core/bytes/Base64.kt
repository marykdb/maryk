package maryk.core.bytes

/** Object to convert base 64 */
expect object Base64 {
    /** Decode [base64] string into bytes array */
    fun decode(base64: String): ByteArray

    /** Encode [bytes] array into a base64 String */
    fun encode(bytes: ByteArray): String
}