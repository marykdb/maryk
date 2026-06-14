package maryk.core.properties.types

import maryk.core.base64.Base64Maryk
import maryk.core.extensions.bytes.initByteArray
import maryk.core.extensions.bytes.writeBytes
import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.compare.compareTo

/**
 * Represents a ByteArray which is comparable
 */
open class Bytes(bytes: ByteArray) : Comparable<Bytes> {
    private val _bytes: ByteArray = bytes.copyOf()
    val bytes: ByteArray get() = _bytes.copyOf()
    val size = bytes.size

    constructor(base64: String) : this(parseBase64Bytes(base64))

    operator fun get(index: Int) = _bytes[index]

    override fun toString() = Base64Maryk.encode(_bytes).trimEnd('=')

    override infix fun compareTo(other: Bytes) = _bytes compareTo other._bytes

    override fun hashCode() = _bytes.contentHashCode()

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is Bytes -> false
        else -> this._bytes contentEquals other._bytes
    }

    fun writeBytes(writer: (byte: Byte) -> Unit) = _bytes.writeBytes(writer)

    fun toHex() = this._bytes.toHexString()

    companion object : BytesDescriptor<Bytes>() {
        override fun invoke(bytes: ByteArray) = Bytes(bytes)
    }
}

/**
 * Generic bytes class to help constructing bytes from different sources
 */
abstract class BytesDescriptor<T> {
    fun fromByteReader(length: Int, reader: () -> Byte) = this(
        initByteArray(length, reader)
    )

    fun ofHex(hex: String) = this(
        hex.hexToByteArray()
    )

    abstract operator fun invoke(bytes: ByteArray): T
}

internal fun parseBase64Bytes(
    base64: String,
    decoder: (String) -> ByteArray = Base64Maryk::decode
) = try {
    decoder(base64)
} catch (e: IllegalArgumentException) {
    throw ParseException(base64, e)
}
