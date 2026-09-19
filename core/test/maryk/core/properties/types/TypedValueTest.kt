package maryk.core.properties.types

import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class TypedValueTest {
    @Test
    fun testPutAndGetFromTypedValue() {
        val value1 = MarykTypeEnum.T2(200)
        val value2 = MarykTypeEnum.T1("test")
        assertEquals(200, value1.value)
        assertEquals("test", value2.value)
    }

    @Test
    fun testPutAndGetFromTypedValueWithType() {
        val value1 = MarykTypeEnum.T2(200)
        assertEquals(200, value1.withTypeOrNull(MarykTypeEnum.T2))
        assertNull((value1 as TypedValue<*, *>).withTypeOrNull(MarykTypeEnum.T1))
    }
}
