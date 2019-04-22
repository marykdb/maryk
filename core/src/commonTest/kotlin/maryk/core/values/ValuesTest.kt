package maryk.core.values

import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option.V2
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class ValuesTest {
    @Test
    fun copy() {
        val original = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = DateTime(2018, 7, 18)
        )

        val copy = original.copy {
            arrayOf(
                string withNotNull "bye world",
                enum withNotNull V2
            )
        }

        copy.size shouldBe 6
        copy { string } shouldBe "bye world"
        copy { enum } shouldBe V2
    }

    @Test
    fun testGetValue() {
        val values = TestMarykModel(
            string = "hello world",
            int = 5,
            uint = 3u,
            double = 2.3,
            dateTime = DateTime(2018, 7, 18),
            listOfString = listOf(
                "v1", "v2", "v3"
            ),
            map = mapOf(
                Time(11, 22, 33) to "eleven",
                Time(12, 23, 34) to "twelve"
            ),
            embeddedValues = EmbeddedMarykModel(
                value = "test",
                model = EmbeddedMarykModel(
                    value = "another test"
                )
            )
        )

        values[TestMarykModel.ref { string }] shouldBe "hello world"
        values[TestMarykModel.ref { double }] shouldBe 2.3
        values[TestMarykModel.ref { dateTime }] shouldBe DateTime(2018, 7, 18)
        values[TestMarykModel { listOfString refAt 2u }] shouldBe "v3"
        values[TestMarykModel { listOfString.refToAny() }] shouldBe listOf("v1", "v2", "v3")
        values[TestMarykModel { map refAt Time(12, 23, 34) }] shouldBe "twelve"
        values[TestMarykModel { embeddedValues.ref { value } }] shouldBe "test"
        values[TestMarykModel { embeddedValues { model.ref { value } } }] shouldBe "another test"
    }
}
