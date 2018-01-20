package maryk.core.bytes

actual fun initString(length: Int, reader: () -> Byte): String {
    var value = ""
    (0 until length).forEach {
        value += reader()
    }
    return value
}

actual fun String.charPointAt(index: Int) = js("this.charCodeAt(index)") as Int