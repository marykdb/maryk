package maryk.core.bytes

external class Buffer(value:String, encoding:String) {
    val length: Int

    fun toString(encoding: String): String
    fun values(): ValueIterator

    companion object {
        fun from(value: ByteArray): Buffer
        fun from(value:String, encoding:String): Buffer
    }

    class ValueIterator {
        fun next() : Value
        class Value {
            val value: Byte
        }
    }
}

/** Util to convert base 64 */
actual object Base64 {
    /** Get String encoded key as bytes
     * @param base64 to decode
     * @return byte representation
     */
    actual fun decode(base64: String): ByteArray {
        val buffer = Buffer.from(base64, "base64")
        val iterable = buffer.values()

        return ByteArray(buffer.length) {
            iterable.next().value
        }
    }

    /** Get Bytes as base64 string
     * @param bytes to encode
     * @return Base64 String
     */
    actual fun encode(bytes: ByteArray): String {
        return Buffer.from(bytes).toString("base64").removeSuffix("=").removeSuffix("=")
    }
}