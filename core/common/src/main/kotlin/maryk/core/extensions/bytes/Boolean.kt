package maryk.core.extensions.bytes

/** Write the bytes of this Boolean to a [writer] */
internal fun Boolean.writeBytes(writer: (byte: Byte) -> Unit) {
    writer(
        if (this) ONE_BYTE else ZERO_BYTE
    )
}

/** Creates a Boolean by reading bytes from [reader] */
internal fun initBoolean(reader: () -> Byte) = reader() != ZERO_BYTE