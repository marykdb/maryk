package maryk.core.properties.types

import maryk.core.extensions.bytes.initByteArray
import maryk.core.extensions.bytes.writeBytes
import maryk.lib.bytes.Base64
import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex

/**
 * Represents a ByteArray which is comparable
 */
open class Bytes(val bytes: ByteArray) : Comparable<Bytes> {
    val size = bytes.size

    constructor(base64: String) : this(
        try {
            Base64.decode(base64)
        } catch (e: Throwable) {
            throw ParseException(base64)
        }
    )

    private val hashCode by lazy { bytes.contentHashCode() }

    operator fun get(index: Int) = bytes[index]

    override fun toString() = Base64.encode(bytes)

    override fun compareTo(other: Bytes) = bytes.compareTo(other.bytes)

    override fun hashCode() = hashCode

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is Bytes -> false
        else -> this.bytes contentEquals other.bytes
    }

    fun writeBytes(writer: (byte: Byte) -> Unit) = bytes.writeBytes(writer)

    fun toHex() = this.bytes.toHex()

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
        initByteArrayByHex(hex)
    )

    abstract operator fun invoke(bytes: ByteArray): T
}
