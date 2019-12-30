package maryk.lib.bytes

import maryk.lib.recyclableByteArray

actual fun initString(bytes: ByteArray, offset: Int, length: Int) =
    String(bytes, offset, length)

actual fun initString(length: Int, reader: () -> Byte) =
    if (length > recyclableByteArray.size) {
        String(
            ByteArray(length) {
                reader()
            }
        )
    } else {
        for (index in 0 until length) {
            recyclableByteArray[index] = reader()
        }
        String(recyclableByteArray, 0, length)
    }

actual fun codePointAt(string: String, index: Int) = Character.codePointAt(string, index)

actual fun fromCodePoint(value: Int): String {
    return Character.toChars(value).joinToString(separator = "")
}
