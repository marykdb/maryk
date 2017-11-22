package maryk.core.properties.definitions

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
            name = "test",
            byteSize = 5
    )

    @Test
    fun createRandom() {
        def.createRandom()
    }

    @Test
    fun testStorageConversion() {
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
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()
        fixedBytesToTest.forEach { checkProtoBufConversion(bc, it, this.def) }
    }

    @Test
    fun convertToString() {
        fixedBytesToTest.forEach {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }
    @Test
    fun convertWrongString() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }
}