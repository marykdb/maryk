package maryk.lib.bytes

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toCValues
import maryk.lib.exceptions.ParseException
import platform.Foundation.NSData
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.posix.uint8_tVar

/** Object to convert base 64 */
actual object Base64 {
    /** Decode [base64] string into bytes array */
    actual fun decode(base64: String): ByteArray {
        // For this library end padding is mandatory so add possible missing padding
        val value = base64.padEnd(
            length = ((base64.length+3)/4)*4,
            padChar = '='
        )

        val data = NSData.create(value, 0) ?: throw ParseException("Invalid Base64 value $base64")
        @Suppress("UNCHECKED_CAST")
        val bytePtr = (data.bytes as CPointer<uint8_tVar>)

        return ByteArray(data.length.toInt()) { index ->
            bytePtr[index].toByte()
        }
    }

    /** Encode [bytes] array into a base64 String */
    actual fun encode(bytes: ByteArray) = memScoped {
        NSData.create(
            bytesNoCopy = bytes.toCValues().getPointer(this),
            length = bytes.size.toULong()
        ).base64EncodedStringWithOptions(0).dropLastWhile {
            // Remove any Base64 padding
            it == '='
        }
    }
}
