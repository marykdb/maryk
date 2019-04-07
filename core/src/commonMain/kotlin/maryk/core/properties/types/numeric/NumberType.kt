package maryk.core.properties.types.numeric

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum

enum class NumberType(
    override val index: UInt,
    val descriptor: () -> NumberDescriptor<*>
) : IndexedEnum<NumberType>, IsCoreEnum {
    SInt8(1u, { maryk.core.properties.types.numeric.SInt8 }),
    SInt16(2u, { maryk.core.properties.types.numeric.SInt16 }),
    SInt32(3u, { maryk.core.properties.types.numeric.SInt32 }),
    SInt64(4u, { maryk.core.properties.types.numeric.SInt64 }),
    UInt8(5u, { maryk.core.properties.types.numeric.UInt8 }),
    UInt16(6u, { maryk.core.properties.types.numeric.UInt16 }),
    UInt32(7u, { maryk.core.properties.types.numeric.UInt32 }),
    UInt64(8u, { maryk.core.properties.types.numeric.UInt64 }),
    Float32(9u, { maryk.core.properties.types.numeric.Float32 }),
    Float64(10u, { maryk.core.properties.types.numeric.Float64 });

    companion object : IndexedEnumDefinition<NumberType>("NumberType", NumberType::values)
}
