package maryk.lib.bytes

import maryk.lib.exceptions.ParseException
import maryk.node.Buffer

/** Util to convert base 64 */
actual object Base64 {
    /** Decode [base64] string into bytes array
     * TODO: Currently only runnable in Node
     */
    actual fun decode(base64: String): ByteArray {
        // Only needed for node
        if (!base64.matches("^[a-zA-Z0-9/+]+[=]{0,2}\$")) {
            throw ParseException("Not allowed characters found in base64 string: $base64")
        }
        val buffer = Buffer.from(base64, "base64")
        val iterable = buffer.values()

        return ByteArray(buffer.length) {
            iterable.next().value
        }
    }

    /** Encode [bytes] array into a base64 String
     * TODO: Currently only runnable in Node
     */
    actual fun encode(bytes: ByteArray): String {
        return Buffer.from(bytes).toString("base64").removeSuffix("=").removeSuffix("=")
    }
}
