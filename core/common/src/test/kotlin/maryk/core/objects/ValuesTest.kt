package maryk.core.objects

import maryk.Option
import maryk.TestMarykModel
import maryk.core.properties.types.numeric.toUInt32
import maryk.lib.time.DateTime
import maryk.test.shouldBe
import kotlin.test.Test

class ValuesTest {
    @Test
    fun copy() {
        val original = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3.toUInt32(),
            double = 2.3,
            dateTime = DateTime(2018, 7, 18)
        )

        val copy = original.copy {
            arrayOf(
                string withNotNull "bye world",
                enum withNotNull Option.V2
            )
        }

        copy.size shouldBe 6
        copy { string } shouldBe "bye world"
        copy { enum } shouldBe Option.V2
    }
}
