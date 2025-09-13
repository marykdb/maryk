package maryk.datastore.memory.records.index

import maryk.core.clock.HLC
import maryk.core.models.key
import maryk.datastore.memory.records.DataRecord
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykModel.value
import kotlin.test.Test
import kotlin.test.expect

class UniqueIndexValuesTest {
    private val valueReference = value.ref().toStorageByteArray()

    private val uniqueIndex =
        UniqueIndexValues<SimpleMarykModel, String>(
            valueReference
        )

    private val simpleValues = SimpleMarykModel.create {
        value with "test"
    }

    private val timestamp1 = HLC(1uL)
    private val timestamp2 = HLC(2uL)
    private val timestamp3 = HLC(3uL)

    // Placeholder without values which are not needed for test
    private val dataRecord = DataRecord(
        key = SimpleMarykModel.key(simpleValues),
        values = listOf(),
        firstVersion = timestamp1,
        lastVersion = timestamp1
    )

    // Placeholder without values which are not needed for test
    private val dataRecord2 = DataRecord(
        key = SimpleMarykModel.key(simpleValues),
        values = listOf(),
        firstVersion = timestamp1,
        lastVersion = timestamp1
    )

    @Test
    fun addToIndex() {
        uniqueIndex.addToIndex(dataRecord, "test", timestamp1)
        expect(dataRecord) { uniqueIndex["test"] }
    }

    @Test
    fun removeIndexAndAdd() {
        val value = "test2"
        uniqueIndex.addToIndex(dataRecord, value, timestamp1)
        expect(dataRecord) { uniqueIndex[value] }
        uniqueIndex.removeFromIndex(dataRecord, value, timestamp2, false)
        expect(null) { uniqueIndex[value] }
        uniqueIndex.addToIndex(dataRecord2, value, timestamp3)
        expect(dataRecord2) { uniqueIndex[value] }
    }

    @Test
    fun removeIndexAndAddHistorical() {
        val value = "test3"
        uniqueIndex.addToIndex(dataRecord, value, timestamp1)
        expect(dataRecord) { uniqueIndex[value] }
        uniqueIndex.removeFromIndex(dataRecord, value, timestamp2, true)
        expect(null) { uniqueIndex[value] }
        uniqueIndex.addToIndex(dataRecord2, value, timestamp3)
        expect(dataRecord2) { uniqueIndex[value] }

        expect(dataRecord) { uniqueIndex[value, timestamp1] }
        expect(null) { uniqueIndex[value, timestamp2] }
        expect(dataRecord2) { uniqueIndex[value, timestamp3] }
    }
}
