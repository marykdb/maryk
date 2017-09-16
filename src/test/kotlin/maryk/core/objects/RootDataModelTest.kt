package maryk.core.objects

import io.kotlintest.matchers.shouldBe
import maryk.Option
import maryk.TestMarykObject
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.numeric.toUInt32
import org.junit.Test

internal class RootDataModelTest {
    @Test
    fun testKey() {
        TestMarykObject.key.getKey(
                TestMarykObject(
                        string = "name",
                        int = 5123123,
                        uint = 555.toUInt32(),
                        double = 6.33,
                        bool = true,
                        enum = Option.V2,
                        dateTime = DateTime.nowUTC()
                )
        ) shouldBe Bytes(
                byteArrayOf(0, 0, 2, 43, 1, 1, 1, 0, 2)
        )
    }
}