package maryk.core.bytes;

actual fun initString(length: Int, reader: () -> Byte) = String(
    ByteArray(length) {
        reader()
    }
)
actual fun String.charPointAt(index: Int) = Character.codePointAt(this, index)