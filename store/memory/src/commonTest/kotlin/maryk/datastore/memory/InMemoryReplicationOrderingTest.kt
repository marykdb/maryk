package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.exceptions.RequestException
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.test.models.Log
import maryk.test.models.Severity.INFO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InMemoryReplicationOrderingTest {
    @Test
    fun rejectsUpdateVersionWhichCannotLeaveClockSuccessor() = runTest {
        val dataStore = InMemoryDataStore.open(dataModelsById = mapOf(1u to Log))
        val rejectedValues = Log(
            message = "unsafe HLC",
            timestamp = LocalDateTime(2026, 1, 1, 0, 0),
        )
        try {
            assertFailsWith<RequestException> {
                dataStore.processUpdate(
                    UpdateResponse(
                        dataModel = Log,
                        update = AdditionUpdate(
                            key = Log.key(rejectedValues),
                            version = ULong.MAX_VALUE - 1uL,
                            firstVersion = ULong.MAX_VALUE - 1uL,
                            insertionIndex = 0,
                            isDeleted = false,
                            values = rejectedValues,
                        ),
                    )
                )
            }

            val localStatus = dataStore.execute(
                Log.add(Log("local mutation", timestamp = LocalDateTime(2026, 1, 1, 1, 0)))
            ).statuses.single()
            assertIs<AddSuccess<Log>>(localStatus)
            assertTrue(dataStore.execute(Log.get(Log.key(rejectedValues))).values.isEmpty())
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun staleObjectCreateDoesNotResurrectHardDeletedRecord() = runTest {
        val dataStore = InMemoryDataStore.open(dataModelsById = mapOf(1u to Log))
        val timestamp = LocalDateTime(2026, 1, 2, 0, 0)
        val values = Log("stale create", timestamp = timestamp)
        val key = Log.key(values)
        try {
            dataStore.processUpdate(
                UpdateResponse(
                    dataModel = Log,
                    update = RemovalUpdate(key, 20uL, HardDelete),
                )
            )
            dataStore.processUpdate(
                UpdateResponse(
                    dataModel = Log,
                    update = ChangeUpdate(
                        key = key,
                        version = 10uL,
                        index = 0,
                        changes = listOf(
                            ObjectCreate,
                            Change(Log { message::ref } with "stale create"),
                            Change(Log { severity::ref } with INFO),
                            Change(Log { this.timestamp::ref } with timestamp),
                        ),
                    ),
                )
            )

            assertTrue(dataStore.execute(Log.get(key)).values.isEmpty())
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun updateHistoryRemainsDescendingAfterOutOfOrderReplication() = runTest {
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = mapOf(1u to Log),
        )
        val newerValues = Log("newer", timestamp = LocalDateTime(2026, 1, 3, 0, 0))
        val olderValues = Log("older", timestamp = LocalDateTime(2026, 1, 3, 1, 0))
        try {
            dataStore.processUpdate(
                UpdateResponse(
                    dataModel = Log,
                    update = AdditionUpdate(Log.key(newerValues), 30uL, 30uL, 0, false, newerValues),
                )
            )
            dataStore.processUpdate(
                UpdateResponse(
                    dataModel = Log,
                    update = AdditionUpdate(Log.key(olderValues), 20uL, 20uL, 0, false, olderValues),
                )
            )

            val updates = dataStore.execute(Log.scanUpdateHistory(fromVersion = 25uL)).updates
            assertEquals(listOf(30uL), updates.map { it.version })
        } finally {
            dataStore.close()
        }
    }
}
