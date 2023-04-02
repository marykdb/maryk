package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt16
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.SInt8
import maryk.core.properties.types.numeric.UInt16
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.properties.types.numeric.UInt8

@Suppress("unused")
object NumericMarykModel : RootDataModel<NumericMarykModel>() {
    val sInt8 by number(
        index = 1u,
        type = SInt8,
        default = 4.toByte()
    )
    val sInt16 by number(
        index = 2u,
        type = SInt16,
        default = 42.toShort()
    )
    val sInt32 by number(
        index = 3u,
        type = SInt32,
        default = 42
    )
    val sInt64 by number(
        index = 4u,
        type = SInt64,
        default = 4123123344572L
    )
    val uInt8 by number(
        index = 5u,
        type = UInt8,
        default = 4.toUByte()
    )
    val uInt16 by number(
        index = 6u,
        type = UInt16,
        default = 42.toUShort()
    )
    val uInt32 by number(
        index = 7u,
        type = UInt32,
        default = 42u
    )
    val uInt64 by number(
        index = 8u,
        type = UInt64,
        default = 4123123344572uL
    )
    val float32 by number(
        index = 9u,
        type = Float32,
        default = 42.345F
    )
    val float64 by number(
        index = 10u,
        type = Float64,
        default = 2345762.3123
    )
}
