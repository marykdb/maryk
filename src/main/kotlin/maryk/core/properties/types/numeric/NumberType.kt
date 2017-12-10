package maryk.core.properties.types.numeric

import maryk.core.properties.types.IndexedEnum

enum class NumberType(override val index: Int) : IndexedEnum<NumberType> {
    SINT8(0),
    SINT16(1),
    SINT32(2),
    SINT64(3),
    UINT8(4),
    UINT16(5),
    UINT32(6),
    UINT64(7),
    FLOAT32(8),
    FLOAT64(9)
}
