package maryk.datastore.remote

import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.WriteCache
import maryk.datastore.shared.rethrowIfFatal

internal object RemoteStoreCodec {
    fun <DO : Any, CX : IsPropertyContext> encode(
        serializer: IsObjectDataModelSerializer<DO, *, CX, CX>,
        value: DO,
        context: CX?,
        maxBytes: Int? = null,
    ): ByteArray {
        val cache = WriteCache()
        val length = serializer.calculateObjectProtoBufLength(value, cache, context)
        if (length < 0) {
            throw IllegalStateException("Proto payload length is negative: $length")
        }
        if (maxBytes != null && length > maxBytes) {
            throw IllegalStateException("Proto payload exceeds max size: $length > $maxBytes")
        }
        val bytes = ByteArray(length)
        var index = 0
        serializer.writeObjectProtoBuf(value, cache, { byte ->
            if (index >= bytes.size) {
                throw IllegalStateException("Proto length mismatch: attempted to write past ${bytes.size} bytes.")
            }
            bytes[index++] = byte
        }, context)
        check(index == bytes.size) { "Proto length mismatch: wrote $index of ${bytes.size} bytes." }
        return bytes
    }

    fun <DO : Any, CX : IsPropertyContext> decode(
        serializer: IsObjectDataModelSerializer<DO, *, CX, CX>,
        bytes: ByteArray,
        context: CX?,
    ): DO {
        var index = 0
        val values = try {
            serializer.readProtoBuf(bytes.size, { bytes[index++] }, context)
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            throw IllegalStateException("Invalid proto payload or trailing bytes", error)
        }
        if (index != bytes.size) {
            throw IllegalStateException("Proto payload has trailing bytes: consumed=$index total=${bytes.size}")
        }
        return try {
            values.toDataObject()
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            throw IllegalStateException("Invalid proto payload or trailing bytes", error)
        }
    }

    fun lengthPrefix(length: Int): ByteArray {
        require(length >= 0) { "Length prefix cannot encode negative lengths." }
        val bytes = ByteArray(4)
        bytes[0] = ((length ushr 24) and 0xFF).toByte()
        bytes[1] = ((length ushr 16) and 0xFF).toByte()
        bytes[2] = ((length ushr 8) and 0xFF).toByte()
        bytes[3] = (length and 0xFF).toByte()
        return bytes
    }

    fun readLengthPrefix(bytes: ByteArray, offset: Int): LengthResult? {
        if (offset < 0) return null
        if (offset > bytes.size - 4) return null
        val length = ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
        return LengthResult(length, offset + 4)
    }

    data class LengthResult(val length: Int, val nextOffset: Int)
}
