package maryk.datastore.memory.processors.changers

import maryk.core.clock.HLC
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SetListValueTest {
    @Test
    fun shrinkingListDeletesOnlyRemovedTailItems() {
        val version = HLC(1uL)
        val nextVersion = HLC(2uL)
        val listRef = TestMarykModel { list::ref }
        val values = mutableListOf<DataRecordNode>(
            DataRecordValue(listRef.toStorageByteArray(), 3, version),
            DataRecordValue(TestMarykModel { list refAt 0u }.toStorageByteArray(), 10, version),
            DataRecordValue(TestMarykModel { list refAt 1u }.toStorageByteArray(), 20, version),
            DataRecordValue(TestMarykModel { list refAt 2u }.toStorageByteArray(), 30, version),
        )

        setListValue(values, listRef, listOf(10, 20), 3, nextVersion, keepAllVersions = false)

        assertEquals(4, values.size)
        assertEquals(2, assertIs<DataRecordValue<Int>>(values[0]).value)
        assertEquals(10, assertIs<DataRecordValue<Int>>(values[1]).value)
        assertEquals(20, assertIs<DataRecordValue<Int>>(values[2]).value)
        assertContentEquals(
            TestMarykModel { list refAt 2u }.toStorageByteArray(),
            assertIs<DeletedValue<Int>>(values[3]).reference
        )
    }
}
