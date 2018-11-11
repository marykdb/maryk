@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.processors.datastore.memory.records

import maryk.lib.time.DateTime
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

class DataRecordValueTreeTest {
    private val simpleValues = SimpleMarykModel(
        value = "simplest"
    )

    private val testMarykValues = TestMarykModel(
        string = "haas",
        int = 9,
        uint = 53u,
        double = 3.5555,
        bool = true,
        dateTime = DateTime(year = 2017, month = 12, day = 5, hour = 12, minute = 40),
        embeddedValues = EmbeddedMarykModel(
            value = "embed",
            model = EmbeddedMarykModel(
                "deeper"
            )
        )
    )

    @Test
    fun convertSimpleValues() {
        val dataRecordValueTree = simpleValues.toDataRecordValueTree(123uL)

        shouldBeOfType<DataRecordValue<*>>(dataRecordValueTree.recordNodes.first())

        dataRecordValueTree.toValues(SimpleMarykModel) { values, version ->
            version shouldBe 123uL
            values shouldBe simpleValues
        }
    }

    @Test
    fun convertComplexValues() {
        val dataRecordValueTree = testMarykValues.toDataRecordValueTree(123uL)

        shouldBeOfType<DataRecordValueTreeNode<*, *>>(dataRecordValueTree.recordNodes.last())

        dataRecordValueTree.toValues(TestMarykModel) { values, version ->
            version shouldBe 123uL
            values shouldBe testMarykValues
        }
    }
}
