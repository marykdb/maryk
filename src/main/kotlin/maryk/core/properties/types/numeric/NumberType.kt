package maryk.core.properties.types.numeric

import maryk.core.properties.types.IndexedEnum

enum class NumberType(
        override val index: Int,
        val descriptor: ()-> NumberDescriptor<*>
) : IndexedEnum<NumberType> {
    SINT8(0, { SInt8 }),
    SINT16(1, { SInt16 }),
    SINT32(2, { SInt32 }),
    SINT64(3, { SInt64 }),
    UINT8(4, { UInt8 }),
    UINT16(5, { UInt16 }),
    UINT32(6, { UInt32 }),
    UINT64(7, { UInt64 }),
    FLOAT32(8, { Float32 }),
    FLOAT64(9, { Float64 })
}
