package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.Option
import maryk.test.models.Option.V1
import maryk.test.models.Option.V2
import maryk.test.models.Option.V3
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class EnumDefinitionTest {
    private val enumsToTest = arrayOf(
        V1,
        V2
    )

    val def = EnumDefinition(
        enum = Option
    )

    val defMaxDefined = EnumDefinition(
        required = false,
        final = true,
        unique = true,
        minValue = V1,
        maxValue = V3,
        enum = Option,
        default = V2
    )

    @Test
    fun convertValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (it in enumsToTest) {
            bc.reserve(
                def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        val expectedEnums = arrayOf(
            "7001",
            "7002"
        )

        for ((enum, expected) in enumsToTest.zip(expectedEnums)) {
            bc.reserve(
                def.calculateTransportByteLengthWithKey(14, enum, cacheFailer, null)
            )
            def.writeTransportBytesWithKey(14, enum, cacheFailer, bc::write, null)
            val key = ProtoBuf.readKey(bc::read)
            key.tag shouldBe 14
            key.wireType shouldBe WireType.VAR_INT

            bc.bytes!!.toHex() shouldBe expected

            def.readTransportBytes(
                ProtoBuf.getLength(WireType.VAR_INT, bc::read),
                bc::read
            ) shouldBe enum
            bc.reset()
        }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (it in enumsToTest) {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun invalidStringValueShouldThrowException() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, EnumDefinition.Model, null, ::compare)
        checkProtoBufConversion(this.defMaxDefined, EnumDefinition.Model, null, ::compare)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, EnumDefinition.Model, null, ::compare)
        checkJsonConversion(this.defMaxDefined, EnumDefinition.Model, null, ::compare)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, EnumDefinition.Model, null, ::compare)
        checkYamlConversion(this.defMaxDefined, EnumDefinition.Model, null, ::compare) shouldBe """
        required: false
        final: true
        unique: true
        enum:
          name: Option
          cases:
            1: V1
            2: V2
            3: V3
        minValue: V1
        maxValue: V3
        default: V2

        """.trimIndent()
    }
}

private fun compare(converted: EnumDefinition<*>, original: EnumDefinition<*>) {
    converted shouldBe original
    converted.hashCode() shouldBe original.hashCode()
}
