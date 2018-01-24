package maryk.core.bytes;

actual fun initString(length: Int, reader: () -> Byte) = String(
    ByteArray(length) {
        reader()
    }
)
actual fun codePointAt(string: String, index: Int) = Character.codePointAt(string, index)