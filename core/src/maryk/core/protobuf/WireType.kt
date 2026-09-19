package maryk.core.protobuf

import maryk.lib.exceptions.ParseException

enum class WireType(val type: Byte) {
    VAR_INT(0),
    BIT_64(1),
    LENGTH_DELIMITED(2),
    START_GROUP(3),
    END_GROUP(4),
    BIT_32(5)
}

/**
 * Retrieve wire type from the [byte]
 */
internal fun wireTypeOf(byte: Byte): WireType {
    return WireType.entries.getOrNull(byte.toInt())
        ?: throw ParseException("Unknown WireType $byte")
}
