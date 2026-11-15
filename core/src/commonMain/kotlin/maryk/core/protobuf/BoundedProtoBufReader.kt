package maryk.core.protobuf

import maryk.lib.exceptions.ParseException

/** Nested messages share a position, so reading a byte need not call every enclosing counter. */
internal class BoundedProtoBufReader private constructor(
    private val input: Input,
    private val end: Int,
    private val errorMessage: String,
) {
    private class Input(val reader: () -> Byte, var position: Int = 0)

    val remaining: Int get() = end - input.position
    val read: () -> Byte = bindProtoBufReader(this)

    fun readByte(): Byte {
        if (input.position >= end) throw ParseException(errorMessage)
        input.position++
        return input.reader()
    }

    companion object {
        fun create(reader: () -> Byte, length: Int, errorMessage: String): BoundedProtoBufReader {
            if (length < 0) throw ParseException(errorMessage)
            val parent = protoBufReaderOwner(reader)
            if (parent != null) {
                if (length > parent.remaining) throw ParseException(errorMessage)
                return BoundedProtoBufReader(parent.input, parent.input.position + length, errorMessage)
            }
            return BoundedProtoBufReader(Input(reader), length, errorMessage)
        }
    }
}

internal expect fun bindProtoBufReader(owner: BoundedProtoBufReader): () -> Byte
internal expect fun protoBufReaderOwner(reader: () -> Byte): BoundedProtoBufReader?
