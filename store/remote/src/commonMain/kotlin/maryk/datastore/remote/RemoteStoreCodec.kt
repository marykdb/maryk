package maryk.datastore.remote

import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.WriteCache

internal object RemoteStoreCodec {
    fun <DO : Any, CX : IsPropertyContext> encode(
        serializer: IsObjectDataModelSerializer<DO, *, CX, CX>,
        value: DO,
        context: CX?,
    ): ByteArray {
        val cache = WriteCache()
        val length = serializer.calculateObjectProtoBufLength(value, cache, context)
        val bytes = ByteArray(length)
        var index = 0
        serializer.writeObjectProtoBuf(value, cache, { byte -> bytes[index++] = byte }, context)
        return bytes
    }

    fun <DO : Any, CX : IsPropertyContext> decode(
        serializer: IsObjectDataModelSerializer<DO, *, CX, CX>,
        bytes: ByteArray,
        context: CX?,
    ): DO {
        var index = 0
        val values = serializer.readProtoBuf(bytes.size, { bytes[index++] }, context)
        return values.toDataObject()
    }

    fun lengthPrefix(length: Int): ByteArray {
        val bytes = ByteArray(4)
        bytes[0] = ((length ushr 24) and 0xFF).toByte()
        bytes[1] = ((length ushr 16) and 0xFF).toByte()
        bytes[2] = ((length ushr 8) and 0xFF).toByte()
        bytes[3] = (length and 0xFF).toByte()
        return bytes
    }

    fun readLengthPrefix(bytes: ByteArray, offset: Int): LengthResult? {
        if (offset + 4 > bytes.size) return null
        val length = ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
        return LengthResult(length, offset + 4)
    }

    data class LengthResult(val length: Int, val nextOffset: Int)
}
