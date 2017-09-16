package maryk.core.bytes;

fun initString(length: Int, reader: () -> Byte) = String(
    ByteArray(length) {
        reader()
    }
)

fun String.writeBytes(reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
    val bytes = this.toByteArray()
    reserver(bytes.size)
    bytes.forEach(writer)
}