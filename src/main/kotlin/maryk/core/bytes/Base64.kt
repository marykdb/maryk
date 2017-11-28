package maryk.core.bytes

/** Util to convert base 64 */
expect object Base64 {
    /** Get String encoded key as bytes
     * @param base64 to decode
     * @return byte representation
     */
    fun decode(base64: String): ByteArray

    /** Get Bytes as base64 string
     * @param bytes to encode
     * @return Base64 String
     */
    fun encode(bytes: ByteArray): String
}