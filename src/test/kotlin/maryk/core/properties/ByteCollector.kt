package maryk.core.properties

/** Collects bytes into a byteArray and enables to read them afterwards*/
open class ByteCollector {
    var bytes: ByteArray? = null
    private var writeIndex = 0
    internal var readIndex = 0

    val size: Int get() = bytes!!.size

    fun reserve(count: Int) {
        bytes = ByteArray(count)
    }

    fun write(byte: Byte) {
        bytes!![writeIndex++] = byte
    }

    fun read() = this.bytes!![this.readIndex++]

    open fun reset() {
        writeIndex = 0
        readIndex = 0
    }
}