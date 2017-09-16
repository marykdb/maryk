package maryk.core.extensions.bytes

/** Write the bytes of this Boolean to a writer
 * @param writer to write this Boolean to
 */
internal fun Boolean.writeBytes(writer: (byte: Byte) -> Unit) {
    writer(
            if (this) ONEBYTE else ZEROBYTE
    )
}

/** Converts reader with bytes to Boolean
 * @param reader to read bytes from
 * @return Boolean represented by bytes
 */
internal fun initBoolean(reader: () -> Byte) = reader() != ZEROBYTE