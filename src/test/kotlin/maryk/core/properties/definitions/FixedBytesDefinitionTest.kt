package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Bytes
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class FixedBytesDefinitionTest {
    private val fixedBytesToTest = arrayOf(
            Bytes(ByteArray(5, { 0x00.toByte() } )),
            Bytes(ByteArray(5, { 0xFF.toByte() } )),
            Bytes(ByteArray(5, { if (it % 2 == 1) 0x88.toByte() else 0xFF.toByte() } ))
    )

    val def = FixedBytesDefinition(
            byteSize = 5
    )

    val defMaxDefined = FixedBytesDefinition(
            indexed = true,
            required = false,
            final = true,
            searchable = false,
            unique = true,
            minValue = Bytes.ofHex("0000000000"),
            maxValue = Bytes.ofHex("AAAAAAAAAA"),
            random = true,
            byteSize = 5
    )

    @Test
    fun `create random value`() {
        def.createRandom()
    }

    @Test
    fun `convert values to storage bytes and back`() {
        val bc = ByteCollector()
        fixedBytesToTest.forEach {
            bc.reserve(
                    def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun `convert values to transport bytes and back`() {
        val bc = ByteCollectorWithLengthCacher()
        fixedBytesToTest.forEach { checkProtoBufConversion(bc, it, this.def) }
    }

    @Test
    fun `convert values to String and back`() {
        fixedBytesToTest.forEach {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun `invalid String value should throw exception`() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun `convert definition to ProtoBuf and back`() {
        checkProtoBufConversion(this.def, FixedBytesDefinition)
        checkProtoBufConversion(this.defMaxDefined, FixedBytesDefinition)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, FixedBytesDefinition)
        checkJsonConversion(this.defMaxDefined, FixedBytesDefinition)
    }
}