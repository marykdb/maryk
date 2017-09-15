package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import maryk.core.properties.ByteCollector
import org.junit.Test
import kotlin.test.assertEquals

internal class BooleanKtTest {
    private val booleansToTest = booleanArrayOf(
            true,
            false
    )

    @Test
    fun testConversion() {
        booleansToTest.forEach {
            assertEquals(
                    it,
                    initBoolean(it.toBytes())
            )
        }
    }

    @Test
    fun testOffsetConversion() {
        booleansToTest.forEach {
            val bytes = ByteArray(22)
            assertEquals(
                    it,
                    initBoolean(it.toBytes(bytes, 10), 10)
            )
        }
    }

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        booleansToTest.forEach {
            bc.reserve(1)
            it.writeBytes(bc::write)

            initBoolean(bc::read) shouldBe it
            bc.reset()
        }
    }
}