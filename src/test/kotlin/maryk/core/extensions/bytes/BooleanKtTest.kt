package maryk.core.extensions.bytes

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
}