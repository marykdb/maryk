package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Bytes
import org.junit.Test

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
    fun convertToBytes() {
        fixedBytesToTest.forEach {
            val b = def.convertToBytes(it)
            def.convertFromBytes(b, 0, b.size) shouldBe it
        }
    }


    @Test
    fun convertToPositionedBytes() {
        fixedBytesToTest.forEach {
            val toBytes = ByteArray(22)
            val b = def.convertToBytes(it, toBytes, 10)
            def.convertFromBytes(b, 10, it.size) shouldBe it
        }
    }

    @Test
    fun convertToString() {
        fixedBytesToTest.forEach {
            val b = def.convertToString(it)
            def.convertFromString(b) shouldBe it
        }
    }

    @Test
    fun convertToOptimizedString() {
        fixedBytesToTest.forEach {
            val b = def.convertToString(it, optimized = true)
            def.convertFromString(b, optimized = true) shouldBe it
        }
    }

    @Test
    fun convertWrongString() {
        shouldThrow<ParseException> {
            def.convertFromString("wrong")
        }
    }
}