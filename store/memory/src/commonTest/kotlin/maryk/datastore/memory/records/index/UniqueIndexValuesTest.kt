package maryk.datastore.memory.records.index

import maryk.core.models.key
import maryk.datastore.memory.records.DataRecord
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykModel.Properties
import maryk.test.models.SimpleMarykModel.Properties.value
import maryk.test.shouldBe
import kotlin.test.Test

class UniqueIndexValuesTest {
    private val valueReference = value.ref().toStorageByteArray()

    private val uniqueIndex =
        UniqueIndexValues<SimpleMarykModel, Properties, String>(
            valueReference
        )

    private val simpleValues = SimpleMarykModel("test")

    // Placeholder without values which are not needed for test
    private val dataRecord = DataRecord(
        key = SimpleMarykModel.key(simpleValues),
        values = listOf(),
        firstVersion = 1uL,
        lastVersion = 1uL
    )

    // Placeholder without values which are not needed for test
    private val dataRecord2 = DataRecord(
        key = SimpleMarykModel.key(simpleValues),
        values = listOf(),
        firstVersion = 1uL,
        lastVersion = 1uL
    )

    @Test
    fun addToIndex() {
        uniqueIndex.addToIndex(dataRecord, "test", 1uL)
        uniqueIndex["test"] shouldBe dataRecord
    }

    @Test
    fun removeIndexAndAdd() {
        val value = "test2"
        uniqueIndex.addToIndex(dataRecord, value, 1uL)
        uniqueIndex[value] shouldBe dataRecord
        uniqueIndex.removeFromIndex(dataRecord, value, 2uL, false)
        uniqueIndex[value] shouldBe null
        uniqueIndex.addToIndex(dataRecord2, value, 3uL)
        uniqueIndex[value] shouldBe dataRecord2
    }

    @Test
    fun removeIndexAndAddHistorical() {
        val value = "test3"
        uniqueIndex.addToIndex(dataRecord, value, 1uL)
        uniqueIndex[value] shouldBe dataRecord
        uniqueIndex.removeFromIndex(dataRecord, value, 2uL, true)
        uniqueIndex[value] shouldBe null
        uniqueIndex.addToIndex(dataRecord2, value, 3uL)
        uniqueIndex[value] shouldBe dataRecord2

        uniqueIndex[value, 1uL] shouldBe dataRecord
        uniqueIndex[value, 2uL] shouldBe null
        uniqueIndex[value, 3uL] shouldBe dataRecord2
    }
}
