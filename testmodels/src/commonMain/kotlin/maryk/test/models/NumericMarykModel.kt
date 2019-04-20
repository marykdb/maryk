package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
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

object NumericMarykModel : RootDataModel<NumericMarykModel, NumericMarykModel.Properties>(
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val sInt8 = add(
            index = 1u, name = "sInt8",
            definition = NumberDefinition(
                type = SInt8,
                default = 4.toByte()
            )
        )
        val sInt16 = add(
            index = 2u, name = "sInt16",
            definition = NumberDefinition(
                type = SInt16,
                default = 42.toShort()
            )
        )
        val sInt32 = add(
            index = 3u, name = "sInt32",
            definition = NumberDefinition(
                type = SInt32,
                default = 42
            )
        )
        val sInt64 = add(
            index = 4u, name = "sInt64",
            definition = NumberDefinition(
                type = SInt64,
                default = 4123123344572L
            )
        )
        val uInt8 = add(
            index = 5u, name = "uInt8",
            definition = NumberDefinition(
                type = UInt8,
                default = 4.toUByte()
            )
        )
        val uInt16 = add(
            index = 6u, name = "uInt16",
            definition = NumberDefinition(
                type = UInt16,
                default = 42.toUShort()
            )
        )
        val uInt32 = add(
            index = 7u, name = "uInt32",
            definition = NumberDefinition(
                type = UInt32,
                default = 42u
            )
        )
        val uInt64 = add(
            index = 8u, name = "uInt64",
            definition = NumberDefinition(
                type = UInt64,
                default = 4123123344572uL
            )
        )
        val float32 = add(
            index = 9u, name = "float32",
            definition = NumberDefinition(
                type = Float32,
                default = 42.345F
            )
        )
        val float64 = add(
            index = 10u, name = "float64",
            definition = NumberDefinition(
                type = Float64,
                default = 2345762.3123
            )
        )
    }

    operator fun invoke(
        sInt8: Byte = 4.toByte(),
        sInt16: Short = 42.toShort(),
        sInt32: Int = 42,
        sInt64: Long = 4123123344572L,
        uInt8: UByte = 4.toUByte(),
        uInt16: UShort = 42.toUShort(),
        uInt32: UInt = 42u,
        uInt64: ULong = 4123123344572uL,
        float32: Float = 42.345F,
        float64: Double = 2345762.3123
    ) = values {
        mapNonNulls(
            this.sInt8 with sInt8,
            this.sInt16 with sInt16,
            this.sInt32 with sInt32,
            this.sInt64 with sInt64,
            this.uInt8 with uInt8,
            this.uInt16 with uInt16,
            this.uInt32 with uInt32,
            this.uInt64 with uInt64,
            this.float32 with float32,
            this.float64 with float64
        )
    }
}
