package maryk.core.properties.types.numeric

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.IsCoreEnum

sealed class NumberType(
    index: UInt,
    val descriptor: () -> NumberDescriptor<*>
) : IndexedEnumImpl<NumberType>(index), IsCoreEnum {
    override val name get() = descriptor()::class.simpleName ?: throw DefNotFoundException("Missing enum option name")

    object SInt8Type : NumberType(1u, { SInt8 })
    object SInt16Type : NumberType(2u, { SInt16 })
    object SInt32Type : NumberType(3u, { SInt32 })
    object SInt64Type : NumberType(4u, { SInt64 })
    object UInt8Type : NumberType(5u, { UInt8 })
    object UInt16Type : NumberType(6u, { UInt16 })
    object UInt32Type : NumberType(7u, { UInt32 })
    object UInt64Type : NumberType(8u, { UInt64 })
    object Float32Type : NumberType(9u, { Float32 })
    object Float64Type : NumberType(10u, { Float64 })

    companion object : IndexedEnumDefinition<NumberType>(
        enumClass = NumberType::class,
        values = { listOf(SInt8Type, SInt16Type, SInt32Type, SInt64Type, UInt8Type, UInt16Type, UInt32Type, UInt64Type, Float32Type, Float64Type) }
    )
}
