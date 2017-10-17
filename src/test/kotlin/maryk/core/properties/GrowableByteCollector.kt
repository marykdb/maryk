package maryk.core.properties

/** Collects bytes into a byteArray and enables to read them afterwards*/
class GrowableByteCollector() {
    private var byteList = mutableListOf<Byte>()
    private var reserved: Int = 0
    private var readIndex = 0

    val bytes: ByteArray get() = byteList.toByteArray()
    val size: Int get() = this.reserved

    fun reserve(count: Int) {
        this.reserved += count
    }

    fun write(byte: Byte) {
        byteList.add(byte)
    }

    fun read() = this.bytes[this.readIndex++]

    fun reset() {
        byteList = mutableListOf<Byte>()
        readIndex = 0
        reserved = 0
    }
}