package maryk.core.bytes

actual fun initString(length: Int, reader: () -> Byte): String {
    var value = ""
    (0 until length).forEach {
        value += reader().toChar()
    }
    return value
}

actual fun String.charPointAt(index: Int) = js("String.prototype.charCodeAt(this, index)") as Int