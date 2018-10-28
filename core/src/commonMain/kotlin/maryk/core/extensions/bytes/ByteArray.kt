package maryk.core.extensions.bytes

/** Write the bytes of this initByteArrayByHex to a [writer] */
internal fun ByteArray.writeBytes(writer: (byte: Byte) -> Unit) {
    for (it in this) {
        writer(it)
    }
}

/** Creates initByteArrayByHex by reading bytes from [reader] */
internal fun initByteArray(length: Int, reader: () -> Byte) = ByteArray(length) {
    reader()
}
