package maryk.lib.bytes

import maryk.lib.exceptions.ParseException
import maryk.node.Buffer

val base64RegEx = Regex("^[a-zA-Z0-9/+]+[=]{0,2}\$")

/** Util to convert base 64 */
actual object Base64 {
    /**
     * Decode [base64] string into bytes array
     * Although Buffer is NodeJS specific code it works for browser
     * because Kotlin includes npm library `buffer` as polyfill
     */
    actual fun decode(base64: String): ByteArray {
        if (!base64RegEx.matches(base64)) {
            throw ParseException("Not allowed characters found in base64 string: $base64")
        }
        val buffer = Buffer.from(base64, "base64")
        val iterable = buffer.values()

        return ByteArray(buffer.length) {
            iterable.next().value
        }
    }

    /**
     * Encode [bytes] array into a base64 String
     * Although Buffer is NodeJS specific code it works for browser
     * because Kotlin includes npm library `buffer` as polyfill
     */
    actual fun encode(bytes: ByteArray): String {
        return Buffer.from(bytes).toString("base64").removeSuffix("=").removeSuffix("=")
    }
}
