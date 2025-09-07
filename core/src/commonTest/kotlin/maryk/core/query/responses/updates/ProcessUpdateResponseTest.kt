package maryk.core.query.responses.updates

import maryk.core.models.key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.pairs.with
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class ProcessUpdateResponseTest {
    val key1 = SimpleMarykModel.key("dR9gVdRcSPw2molM1AiOng")
    val key2 = SimpleMarykModel.key("Vc4WgX_mQHYCSEoLtfLSUQ")

    val initialItems = listOf(
        ValuesWithMetaData(
            key = key1,
            firstVersion = 1234uL,
            lastVersion = 2345uL,
            values = SimpleMarykModel.create { value += "v1" },
            isDeleted = false
        ),
        ValuesWithMetaData(
            key = key2,
            firstVersion = 12345uL,
            lastVersion = 23456uL,
            values = SimpleMarykModel.create { value += "v2" },
            isDeleted = false
        )
    )

    @Test
    fun testInitialValues() {
        val initialValuesUpdate = InitialValuesUpdate(
            version = 123456uL,
            values = listOf(
                ValuesWithMetaData(
                    key = key1,
                    firstVersion = 123456uL,
                    lastVersion = 1234568uL,
                    isDeleted = false,
                    values = SimpleMarykModel.create {
                        value += "test value 1"
                    }
                )
            )
        )

        val newItems = processUpdateResponse(initialValuesUpdate, initialItems)

        assertEquals(1, newItems.size)

        newItems[0].apply {
            assertEquals(initialValuesUpdate.values[0], this)
        }
    }

    @Test
    fun testInitialChanges() {
        val initialChangesUpdate = InitialChangesUpdate(
            version = 123456uL,
            changes = listOf(
                DataObjectVersionedChange(
                    key = key1,
                    changes = listOf()
                )
            )
        )

        assertFailsWith<Exception> {
            processUpdateResponse(initialChangesUpdate, initialItems)
        }
    }

    @Test
    fun testAddition() {
        val addition = AdditionUpdate(
            key = SimpleMarykModel.key("0ruQCs38S2QaByYof-IJgA"),
            firstVersion = 3456uL,
            version = 4567uL,
            insertionIndex = 1,
            values = SimpleMarykModel.create {
                value += "v3"
            },
            isDeleted = false
        )

        val newItems = processUpdateResponse(addition, initialItems)

        assertEquals(3, newItems.size)

        newItems[1].apply {
            assertEquals(addition.key, key)
            assertEquals(addition.values, values)
            assertEquals(addition.firstVersion, firstVersion)
            assertEquals(addition.version, lastVersion)
            assertEquals(false, isDeleted)
        }
    }

    @Test
    fun testChangeInPlace() {
        val newValue = "new value"

        val change = ChangeUpdate(
            key = key2,
            version = 4567uL,
            index = 1,
            changes = listOf(
                Change(
                    SimpleMarykModel { value::ref } with newValue
                )
            )
        )

        val newItems = processUpdateResponse(change, initialItems)

        assertEquals(2, newItems.size)

        newItems[1].apply {
            assertEquals(key2, key)
            assertEquals(newValue, values { value })
        }
    }

    @Test
    fun testChangeWithMove() {
        val newValue = "new value"

        val change = ChangeUpdate(
            key = key2,
            version = 4567uL,
            index = 0,
            changes = listOf(
                Change(
                    SimpleMarykModel { value::ref } with newValue
                )
            )
        )

        val newItems = processUpdateResponse(change, initialItems)

        assertEquals(2, newItems.size)

        newItems[0].apply {
            assertEquals(key2, key)
            assertEquals(newValue, values { value })
        }
    }

    @Test
    fun testRemoval() {
        val removal = RemovalUpdate(
            key = key1,
            version = 4567uL,
            reason = HardDelete
        )

        val newItems = processUpdateResponse(removal, initialItems)

        assertEquals(1, newItems.size)

        assertEquals(key2, newItems[0].key)
    }
}
