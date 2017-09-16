package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.TestMarykObject
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Key
import org.junit.Test

internal class ReferenceDefinitionTest {
    private val refToTest = arrayOf(
            Key<TestMarykObject>(ByteArray(9, { 0x00.toByte() })),
            Key<TestMarykObject>(ByteArray(9, { 0xFF.toByte() })),
            Key<TestMarykObject>(ByteArray(9, { if (it % 2 == 1) 0x88.toByte() else 0xFF.toByte() }))
    )

    val def = ReferenceDefinition(
            name = "test",
            dataModel = TestMarykObject
    )

    @Test
    fun hasValues() {
        def.dataModel shouldBe TestMarykObject
    }

    @Test
    fun convertToBytes() {
        refToTest.forEach {
            val b = def.convertToBytes(it)
            def.convertFromBytes(b, 0, b.size) shouldBe it
        }
    }


    @Test
    fun convertToPositionedBytes() {
        refToTest.forEach {
            val toBytes = ByteArray(22)
            val b = def.convertToBytes(it, toBytes, 10)
            def.convertFromBytes(b, 10, it.size) shouldBe it
        }
    }

    @Test
    fun convertToString() {
        refToTest.forEach {
            val b = def.convertToString(it)
            def.convertFromString(b) shouldBe it
        }
    }

    @Test
    fun convertToOptimizedString() {
        refToTest.forEach {
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

    @Test
    fun testStreamingConversion() {
        val byteCollector = ByteCollector()
        refToTest.forEach {
            def.convertToBytes(it, byteCollector::reserve, byteCollector::write)
            def.convertFromBytes(byteCollector.size, byteCollector::read) shouldBe it
            byteCollector.reset()
        }
    }
}