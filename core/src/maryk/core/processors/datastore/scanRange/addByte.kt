package maryk.core.processors.datastore.scanRange

internal fun addByte(list: MutableList<MutableList<Byte>>, byte: Byte) {
    for (byteList in list) {
        byteList += byte
    }
}
