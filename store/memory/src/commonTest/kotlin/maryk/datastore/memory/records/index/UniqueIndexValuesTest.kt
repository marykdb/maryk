package maryk.datastore.memory.records.index

import maryk.core.models.key
import maryk.datastore.memory.records.DataRecord
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykModel.Properties
import maryk.test.models.SimpleMarykModel.Properties.value
import kotlin.test.Test
import kotlin.test.expect

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
        expect(dataRecord) { uniqueIndex["test"] }
    }

    @Test
    fun removeIndexAndAdd() {
        val value = "test2"
        uniqueIndex.addToIndex(dataRecord, value, 1uL)
        expect(dataRecord) { uniqueIndex[value] }
        uniqueIndex.removeFromIndex(dataRecord, value, 2uL, false)
        expect(null) { uniqueIndex[value] }
        uniqueIndex.addToIndex(dataRecord2, value, 3uL)
        expect(dataRecord2) { uniqueIndex[value] }
    }

    @Test
    fun removeIndexAndAddHistorical() {
        val value = "test3"
        uniqueIndex.addToIndex(dataRecord, value, 1uL)
        expect(dataRecord) { uniqueIndex[value] }
        uniqueIndex.removeFromIndex(dataRecord, value, 2uL, true)
        expect(null) { uniqueIndex[value] }
        uniqueIndex.addToIndex(dataRecord2, value, 3uL)
        expect(dataRecord2) { uniqueIndex[value] }

        expect(dataRecord) { uniqueIndex[value, 1uL] }
        expect(null) { uniqueIndex[value, 2uL] }
        expect(dataRecord2) { uniqueIndex[value, 3uL] }
    }
}
