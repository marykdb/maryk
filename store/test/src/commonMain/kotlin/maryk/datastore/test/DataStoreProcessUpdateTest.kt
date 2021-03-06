package maryk.datastore.test

import maryk.core.exceptions.RequestException
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.services.responses.UpdateResponse
import maryk.datastore.shared.IsDataStore
import maryk.lib.time.DateTime
import maryk.test.assertType
import maryk.test.models.Log
import maryk.test.models.Severity.ERROR
import maryk.test.runSuspendingTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.expect

class DataStoreProcessUpdateTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    override val allTests = mapOf(
        "executeProcessAddRequest" to ::executeProcessAddRequest,
        "executeProcessChangeRequest" to ::executeProcessChangeRequest,
        "executeProcessAddInChangeRequest" to ::executeProcessAddInChangeRequest,
        "executeProcessRemovalRequest" to ::executeProcessRemovalRequest,
        "executeProcessInitialChangesRequest" to ::executeProcessInitialChangesRequest,
        "failOnInitialValuesRequest" to ::failOnInitialValuesUpdateRequest,
        "failOnOrderedKeysUpdateRequest" to ::failOnOrderedKeysUpdateRequest
    )

    private val logs = arrayOf(
        Log("Something happened", timestamp = DateTime(2018, 11, 14, 11, 22, 33, 40)),
        Log("Something else happened", timestamp = DateTime(2018, 11, 14, 12, 0, 0, 0)),
        Log("Something REALLY happened", timestamp = DateTime(2018, 11, 14, 12, 33, 22, 111)),
        Log("WRONG", ERROR, DateTime(2018, 11, 14, 13, 0, 2, 0))
    )

    private val keys = listOf(
        Log.key(logs[0]),
        Log.key(logs[1]),
        Log.key(logs[2]),
        Log.key(logs[3])
    )

    override fun resetData() {
        runSuspendingTest {
            dataStore.execute(
                Log.delete(*keys.toTypedArray(), hardDelete = true)
            )
        }
    }

    private fun executeProcessAddRequest() = runSuspendingTest {
        val addResponse = dataStore.processUpdate(
            UpdateResponse(
                id = 1234uL,
                dataModel = Log,
                update = AdditionUpdate(
                    key = keys[0],
                    version = 1234uL,
                    firstVersion = 1234uL,
                    insertionIndex = 1,
                    isDeleted = false,
                    values = logs[0]
                )
            )
        )

        assertType<AddResponse<*>>(addResponse.result).apply {
            assertEquals(1, statuses.size)
            assertType<AddSuccess<*>>(statuses.first())
        }

        val getResponse = dataStore.execute(
            Log.get(keys[0])
        )

        expect(1) { getResponse.values.size }
        expect(logs[0]) { getResponse.values.first().values }
    }

    private fun executeProcessChangeRequest() = runSuspendingTest {
        dataStore.processUpdate(
            UpdateResponse(
                id = 1234uL,
                dataModel = Log,
                update = AdditionUpdate(
                    key = keys[0],
                    version = 1234uL,
                    firstVersion = 1234uL,
                    insertionIndex = 1,
                    isDeleted = false,
                    values = logs[0]
                )
            )
        )

        val editedMessage = "Edited message"
        val changeResponse = dataStore.processUpdate(
            UpdateResponse(
                id = 1235uL,
                dataModel = Log,
                update = ChangeUpdate(
                    key = keys[0],
                    version = 1235uL,
                    index = 1,
                    changes = listOf(
                        Change(Log { message::ref } with editedMessage)
                    )
                )
            )
        )

        assertType<ChangeResponse<*>>(changeResponse.result).apply {
            assertEquals(1, statuses.size)
            assertType<ChangeSuccess<*>>(statuses.first())
        }

        val getResponse = dataStore.execute(
            Log.get(keys[0])
        )

        expect(1) { getResponse.values.size }
        expect(editedMessage) { getResponse.values.first().values { message } }
    }

    private fun executeProcessAddInChangeRequest() = runSuspendingTest {
        val newMessage = "New message"
        val changeResponse = dataStore.processUpdate(
            UpdateResponse(
                id = 1234uL,
                dataModel = Log,
                update = ChangeUpdate(
                    key = keys[0],
                    version = 1234uL,
                    index = 1,
                    changes = listOf(
                        ObjectCreate,
                        Change(Log { message::ref } with newMessage),
                        Change(Log { severity::ref } with ERROR),
                        Change(Log { timestamp::ref } with DateTime(2020, 9, 5, 12))
                    )
                )
            )
        )

        assertType<AddResponse<*>>(changeResponse.result).apply {
            assertEquals(1, statuses.size)
            assertType<AddSuccess<*>>(statuses.first())
        }

        val getResponse = dataStore.execute(
            Log.get(keys[0])
        )

        expect(1) { getResponse.values.size }
        expect(newMessage) { getResponse.values.first().values { message } }
    }

    private fun executeProcessRemovalRequest() = runSuspendingTest {
        dataStore.processUpdate(
            UpdateResponse(
                id = 1234uL,
                dataModel = Log,
                update = AdditionUpdate(
                    key = keys[0],
                    version = 1234uL,
                    firstVersion = 1234uL,
                    insertionIndex = 1,
                    isDeleted = false,
                    values = logs[0]
                )
            )
        )

        dataStore.processUpdate(
            UpdateResponse(
                id = 1234uL,
                dataModel = Log,
                update = AdditionUpdate(
                    key = keys[1],
                    version = 1234uL,
                    firstVersion = 1234uL,
                    insertionIndex = 1,
                    isDeleted = false,
                    values = logs[1]
                )
            )
        )

        val hardRemovalUpdate = dataStore.processUpdate(
            UpdateResponse(
                id = 1235uL,
                dataModel = Log,
                update = RemovalUpdate(
                    key = keys[0],
                    version = 1235uL,
                    reason = HardDelete
                )
            )
        )

        assertType<DeleteResponse<*>>(hardRemovalUpdate.result).apply {
            assertEquals(1, statuses.size)
            assertType<DeleteSuccess<*>>(statuses.first())
        }

        val getResponse1 = dataStore.execute(
            Log.get(keys[0])
        )

        expect(0) { getResponse1.values.size }

        val softRemovalUpdate = dataStore.processUpdate(
            UpdateResponse(
                id = 1235uL,
                dataModel = Log,
                update = RemovalUpdate(
                    key = keys[1],
                    version = 1235uL,
                    reason = SoftDelete
                )
            )
        )

        assertType<DeleteResponse<*>>(softRemovalUpdate.result).apply {
            assertEquals(1, statuses.size)
            assertType<DeleteSuccess<*>>(statuses.first())
        }

        val getResponse2 = dataStore.execute(
            Log.get(keys[1], filterSoftDeleted = false)
        )

        expect(1) { getResponse2.values.size }
        assertTrue(getResponse2.values[0].isDeleted)
    }

    private fun executeProcessInitialChangesRequest() = runSuspendingTest {
        dataStore.processUpdate(
            UpdateResponse(
                id = 1234uL,
                dataModel = Log,
                update = AdditionUpdate(
                    key = keys[0],
                    version = 1234uL,
                    firstVersion = 1234uL,
                    insertionIndex = 1,
                    isDeleted = false,
                    values = logs[0]
                )
            )
        )

        val newMessage = "New message"
        val editedMessage = "Initially edited message"
        val changeResponse = dataStore.processUpdate(
            UpdateResponse(
                id = 1234uL,
                dataModel = Log,
                update = InitialChangesUpdate(
                    version = 1234uL,
                    changes = listOf(
                        DataObjectVersionedChange(
                            key = keys[0],
                            changes = listOf(VersionedChanges(
                                version = 1234uL,
                                changes = listOf(
                                    Change(Log { message::ref } with editedMessage)
                                )
                            ))
                        ),
                        DataObjectVersionedChange(
                            key = keys[1],
                            changes = listOf(VersionedChanges(
                                version = 1234uL,
                                changes = listOf(
                                    ObjectCreate,
                                    Change(Log { message::ref } with newMessage),
                                    Change(Log { severity::ref } with ERROR),
                                    Change(Log { timestamp::ref } with DateTime(2020, 9, 5, 12))
                                )
                            ))
                        )
                    )
                )
            )
        )

        assertType<AddOrChangeResponse<*>>(changeResponse.result).apply {
            assertEquals(2, statuses.size)
            assertType<ChangeSuccess<*>>(statuses[0])
            assertType<AddSuccess<*>>(statuses[1])
        }

        val getResponse = dataStore.execute(
            Log.get(keys[0], keys[1])
        )

        expect(2) { getResponse.values.size }
        expect(editedMessage) { getResponse.values[0].values { message } }
        expect(newMessage) { getResponse.values[1].values { message } }
    }

    private fun failOnInitialValuesUpdateRequest() = runSuspendingTest {
        assertFailsWith<RequestException> {
            dataStore.processUpdate(
                UpdateResponse(
                    id = 1234uL,
                    dataModel = Log,
                    update = InitialValuesUpdate(
                        version = 12345uL,
                        values = listOf()
                    )
                )
            )
        }
    }

    private fun failOnOrderedKeysUpdateRequest() = runSuspendingTest {
        assertFailsWith<RequestException> {
            dataStore.processUpdate(
                UpdateResponse(
                    id = 1234uL,
                    dataModel = Log,
                    update = OrderedKeysUpdate(
                        keys = keys,
                        version = 12345uL
                    )
                )
            )
        }
    }
}
