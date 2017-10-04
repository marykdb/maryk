package maryk.core.protobuf

import maryk.core.properties.exceptions.ParseException


enum class WireType(val type: Byte) {
    VAR_INT(0),
    BIT_64(1),
    LENGTH_DELIMITED(2),
    START_GROUP(3),
    END_GROUP(4),
    BIT_32(5)
}

internal fun wireTypeOf(byte: Byte): WireType {
    for (wireType in WireType.values()) {
        if (byte == wireType.type) {
            return wireType
        }
    }
    throw ParseException("Unknown WireType $byte")
}
