package maryk.core.protobuf

import maryk.core.extensions.bytes.SEVEN_BYTES
import maryk.core.extensions.bytes.SIGN_BYTE
import maryk.core.extensions.bytes.ZERO_BYTE
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.exceptions.ParseException
import kotlin.experimental.and
import kotlin.experimental.xor

internal object ProtoBuf {
    /** Write the key for protobuf field */
    internal fun writeKey(tag: Int, wireType: WireType, writer: (byte: Byte) -> Unit) {
        val byteSize = tag.calculateTagByteSize()

        // Write Tag + Wiretype + potential sign byte (STTT TWWW)
        writer(
            (
                    ((tag shl 3).toByte() and 0b0111_1000) // Add first part of tag to byte
                            xor wireType.type // Add Type to byte
                    ) xor if (byteSize > 1) SIGN_BYTE else ZERO_BYTE // Add Sign byte if total is longer than 5 bytes
        )
        // Write any needed extra byte for the tag as a VarInt
        if (byteSize > 1) {
            for (it in 1 until byteSize) {
                val isLast = it == byteSize - 1
                writer(
                    (tag shr (7*it-3)).toByte() and SEVEN_BYTES xor if(isLast) ZERO_BYTE else SIGN_BYTE
                )
            }
        }
    }

    /** Reads the key of a ProtoBuf based field from [reader] into a ProtoBufKey */
    internal fun readKey(reader: () -> Byte) : ProtoBufKey {
        var byte = reader()
        val wireType = wireTypeOf(byte and 0b111)

        var result = (byte and 0b0111_1000).toInt() shr 3
        if (byte and SIGN_BYTE == ZERO_BYTE) {
            return ProtoBufKey(result, wireType)
        }

        var shift = 4
        while (shift < 35) {
            byte = reader()
            result = result or ((byte and 0b0111_1111).toInt() shl shift)
            if (byte and SIGN_BYTE == ZERO_BYTE) {
                return ProtoBufKey(result, wireType)
            }
            shift += 7
        }
        throw ParseException("Too big tag")
    }

    /** Skips a field in [reader] by [wireType] */
    internal fun skipField(wireType: Any, reader: () -> Byte) {
        when (wireType) {
            WireType.VAR_INT -> {
                var currentByte: Byte
                do {
                    currentByte = reader()
                } while (currentByte and SIGN_BYTE != ZERO_BYTE)
            }
            WireType.BIT_64 -> for (it in 0 until 8) {
                reader()
            }
            WireType.LENGTH_DELIMITED -> (0 until initIntByVar(reader)).forEach { reader() }
            WireType.START_GROUP -> {
                while (true) {
                    val key = ProtoBuf.readKey(reader)
                    if (key.wireType == WireType.END_GROUP) {
                        break
                    }
                    skipField(key.wireType, reader)
                }
            }
            WireType.END_GROUP -> return
            WireType.BIT_32 -> for (it in 0 until 4) {
                reader()
            }
        }
    }

    /**
     * Get length of next value by [wireType] and reading from [reader]
     * It is -1 for varInt or start/end group
     */
    internal fun getLength(wireType: WireType, reader: () -> Byte) = when(wireType) {
        WireType.VAR_INT -> -1
        WireType.BIT_64 -> 8
        WireType.LENGTH_DELIMITED -> initIntByVar(reader)
        WireType.START_GROUP -> -1
        WireType.END_GROUP -> -1
        WireType.BIT_32 -> 4
    }

    /** Calculate the length of the key [tag] */
    internal fun calculateKeyLength(tag: Int): Int {
        return tag.calculateTagByteSize()
    }
}

/** Calculates the byte length of the variable int */
private fun Int.calculateTagByteSize(): Int = when {
    this and (Int.MAX_VALUE shl 4) == 0 -> 1
    this and (Int.MAX_VALUE shl 11) == 0 -> 2
    this and (Int.MAX_VALUE shl 18) == 0 -> 3
    this and (Int.MAX_VALUE shl 25) == 0 -> 4
    else -> 5
}

/** Contains the tag and wiretype of the Protobuf key */
internal class ProtoBufKey(val tag: Int, val wireType: WireType)