package maryk.core.properties.types.numeric

import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum

sealed class NumberType(
    index: UInt,
    val descriptor: () -> NumberDescriptor<*>
) : IndexedEnumImpl<NumberType>(index), IsCoreEnum {
    object SInt8 : NumberType(1u, { maryk.core.properties.types.numeric.SInt8 }), TypeEnum<Byte>
    object SInt16 : NumberType(2u, { maryk.core.properties.types.numeric.SInt16 }), TypeEnum<Short>
    object SInt32 : NumberType(3u, { maryk.core.properties.types.numeric.SInt32 }), TypeEnum<Int>
    object SInt64 : NumberType(4u, { maryk.core.properties.types.numeric.SInt64 }), TypeEnum<Long>
    object UInt8 : NumberType(5u, { maryk.core.properties.types.numeric.UInt8 }), TypeEnum<UByte>
    object UInt16 : NumberType(6u, { maryk.core.properties.types.numeric.UInt16 }), TypeEnum<UShort>
    object UInt32 : NumberType(7u, { maryk.core.properties.types.numeric.UInt32 }), TypeEnum<UInt>
    object UInt64 : NumberType(8u, { maryk.core.properties.types.numeric.UInt64 }), TypeEnum<ULong>
    object Float32 : NumberType(9u, { maryk.core.properties.types.numeric.Float32 }), TypeEnum<Float>
    object Float64 : NumberType(10u, { maryk.core.properties.types.numeric.Float64 }), TypeEnum<Double>

    companion object : IndexedEnumDefinition<NumberType>(
        enumClass = NumberType::class,
        values = { arrayOf(SInt8, SInt16, SInt32, SInt64, UInt8, UInt16, UInt32, UInt64, Float32, Float64) }
    )
}
