package maryk.core.protobuf

import maryk.core.extensions.bytes.SIGN_BYTE
import maryk.core.extensions.bytes.ZERO_BYTE
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initIntByVarWithExtraInfo
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import kotlin.experimental.and

internal object ProtoBuf {
    /** Write the key for ProtoBuf field */
    internal fun writeKey(tag: Int, wireType: WireType, writer: (byte: Byte) -> Unit) {
        tag.writeVarIntWithExtraInfo(wireType.type, writer)
    }

    /** Reads the key of a ProtoBuf based field from [reader] into a ProtoBufKey */
    internal fun readKey(reader: () -> Byte): ProtoBufKey {
        return initIntByVarWithExtraInfo(reader) { result, wireByte ->
            ProtoBufKey(result, wireTypeOf(wireByte and 0b111))
        }
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
            WireType.LENGTH_DELIMITED -> for (it in 0 until initIntByVar(reader)) {
                reader()
            }
            WireType.START_GROUP -> {
                while (true) {
                    val key = readKey(reader)
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
    internal fun getLength(wireType: WireType, reader: () -> Byte) = when (wireType) {
        WireType.VAR_INT -> -1
        WireType.BIT_64 -> 8
        WireType.LENGTH_DELIMITED -> initIntByVar(reader)
        WireType.START_GROUP -> -1
        WireType.END_GROUP -> -1
        WireType.BIT_32 -> 4
    }

    /** Calculate the length of the key [tag] */
    internal fun calculateKeyLength(tag: Int): Int {
        return tag.calculateVarIntWithExtraInfoByteSize()
    }
}

/** Contains the tag and wiretype of the Protobuf key */
internal class ProtoBufKey(val tag: Int, val wireType: WireType)
