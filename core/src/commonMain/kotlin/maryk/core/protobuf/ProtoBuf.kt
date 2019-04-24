package maryk.core.protobuf

import maryk.core.extensions.bytes.SIGN_BYTE
import maryk.core.extensions.bytes.ZERO_BYTE
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.protobuf.WireType.BIT_32
import maryk.core.protobuf.WireType.BIT_64
import maryk.core.protobuf.WireType.END_GROUP
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WireType.START_GROUP
import maryk.core.protobuf.WireType.VAR_INT
import kotlin.experimental.and

internal object ProtoBuf {
    /** Write the key for ProtoBuf field */
    internal fun writeKey(tag: UInt, wireType: WireType, writer: (byte: Byte) -> Unit) {
        tag.writeVarIntWithExtraInfo(wireType.type, writer)
    }

    /** Reads the key of a ProtoBuf based field from [reader] into a ProtoBufKey */
    internal fun readKey(reader: () -> Byte): ProtoBufKey {
        return initUIntByVarWithExtraInfo(reader) { result, wireByte ->
            ProtoBufKey(result, wireTypeOf(wireByte and 0b111))
        }
    }

    /** Skips a field in [reader] by [wireType] */
    internal fun skipField(wireType: Any, reader: () -> Byte) {
        when (wireType) {
            VAR_INT -> {
                var currentByte: Byte
                do {
                    currentByte = reader()
                } while (currentByte and SIGN_BYTE != ZERO_BYTE)
            }
            BIT_64 -> for (it in 0 until 8) {
                reader()
            }
            LENGTH_DELIMITED -> for (it in 0 until initIntByVar(reader)) {
                reader()
            }
            START_GROUP -> {
                while (true) {
                    val key = readKey(reader)
                    if (key.wireType == END_GROUP) {
                        break
                    }
                    skipField(key.wireType, reader)
                }
            }
            END_GROUP -> return
            BIT_32 -> for (it in 0 until 4) {
                reader()
            }
        }
    }

    /**
     * Get length of next value by [wireType] and reading from [reader]
     * It is -1 for varInt or start/end group
     */
    internal fun getLength(wireType: WireType, reader: () -> Byte) = when (wireType) {
        VAR_INT -> -1
        BIT_64 -> 8
        LENGTH_DELIMITED -> initIntByVar(reader)
        START_GROUP -> -1
        END_GROUP -> -1
        BIT_32 -> 4
    }

    /** Calculate the length of the key [tag] */
    internal fun calculateKeyLength(tag: UInt): Int {
        return tag.calculateVarIntWithExtraInfoByteSize()
    }
}

/** Contains the tag and wiretype of the Protobuf key */
internal class ProtoBufKey(val tag: UInt, val wireType: WireType)
