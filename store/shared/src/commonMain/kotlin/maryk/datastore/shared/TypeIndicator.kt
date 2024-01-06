package maryk.datastore.shared

/**
 * Indicates type of value
 */
enum class TypeIndicator(val byte: Byte) {
    DeletedIndicator(0),
    NoTypeIndicator(1),
    SimpleTypeIndicator(2),
    ComplexTypeIndicator(3),
    EmbedIndicator(4);

    val byteArray = byteArrayOf(byte)
}
