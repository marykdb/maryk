package maryk.core.properties.types

import maryk.core.bytes.Base64
import maryk.core.extensions.bytes.initByteArray
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.compare.compareTo
import maryk.core.extensions.initByteArrayByHex
import maryk.core.extensions.toHex
import maryk.core.properties.exceptions.ParseException

open class Bytes(val bytes: ByteArray): Comparable<Bytes> {
    val size = bytes.size

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

    companion object: BytesDescriptor<Bytes>() {
        override fun construct(bytes: ByteArray) = Bytes(bytes)
    }
}

abstract class BytesDescriptor<T>{
    fun ofBase64String(base64: String) = try {
        this.construct(
                Base64.decode(base64)
        )
    } catch (e: Throwable) { throw ParseException(base64) }

    fun fromByteReader(length: Int, reader: () -> Byte): T = this.construct(
        initByteArray(length, reader)
    )

    fun ofHex(hex: String) = this.construct(
        initByteArrayByHex(hex)
    )

    internal abstract fun construct(bytes: ByteArray): T
}
