package maryk.core.extensions.bytes

fun ByteArray.toBytes(destination: ByteArray, offset: Int = 0): ByteArray {
    this.forEachIndexed {
        index, byte -> destination[index + offset] = byte
    }
    return destination
}

fun initByteArray(byteArray: ByteArray, offset: Int = 0, length: Int = byteArray.size) = when {
    length == byteArray.size && offset == 0 -> byteArray
    else ->byteArray.copyOfRange(offset, offset + length)
}