package maryk.core.properties.types

import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals

internal class TypedValueTest {
    @Test
    fun testPutAndGetFromTypedValue() {
        val value1 = MarykTypeEnum.T2(200)
        val value2 = MarykTypeEnum.T1("test")
        assertEquals(200, value1.getValue())
        assertEquals("test", value2.getValue())
    }
}
