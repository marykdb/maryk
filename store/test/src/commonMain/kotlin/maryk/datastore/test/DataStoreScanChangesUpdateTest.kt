package maryk.datastore.test

import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Exists
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scanChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.lib.time.DateTime
import maryk.test.assertType
import maryk.test.models.TestMarykModel
import maryk.test.runSuspendingTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DataStoreScanChangesUpdateTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<TestMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "failWithMutableWhereClause" to ::failWithMutableWhereClause,
        "executeScanChangesAsFlowRequest" to ::executeScanChangesAsFlowRequest,
        "executeOrderedScanChangesAsFlowRequest" to ::executeOrderedScanChangesAsFlowRequest
    )

    override fun initData() {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                TestMarykModel.add(
                    TestMarykModel(
                        string = "ha world 1",
                        int = 3,
                        uint = 67u,
                        bool = true,
                        double = 2323.3,
                        dateTime = DateTime(1989, 9, 8)
                    ),
                    TestMarykModel(
                        string = "ha world 2",
                        int = 1,
                        uint = 69u,
                        bool = false,
                        double = 0.1,
                        dateTime = DateTime(2001, 4, 2)
                    ),
                    TestMarykModel(
                        string = "ha world 3",
                        int = 4,
                        uint = 1244u,
                        bool = true,
                        double = 444.0,
                        dateTime = DateTime(2005, 11, 30)
                    ),
                    TestMarykModel(
                        string = "ha world 4",
                        int = 2,
                        uint = 52323u,
                        bool = false,
                        double = 2333.0,
                        dateTime = DateTime(2012, 1, 28)
                    ),
                    TestMarykModel(
                        string = "ha world 5",
                        int = 5,
                        uint = 234234u,
                        bool = true,
                        double = 232523.3,
                        dateTime = DateTime(2020, 3, 23)
                    )
                )
            )
            addResponse.statuses.forEach { status ->
                val response = assertType<AddSuccess<TestMarykModel>>(status)
                keys.add(response.key)
                if (response.version < lowestVersion) {
                    // Add lowest version for scan test
                    lowestVersion = response.version
                }
            }
        }
    }

    override fun resetData() {
        runSuspendingTest {
            dataStore.execute(
                TestMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
            )
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private fun failWithMutableWhereClause() = runSuspendingTest {
        assertFailsWith<RequestException> {
            dataStore.executeFlow(
                TestMarykModel.getChanges(keys[0], keys[1], where = Exists(TestMarykModel { string::ref }))
            )
        }
    }

    private fun executeScanChangesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanChanges(
                startKey = keys[1]
            ),
            4
        ) { responses ->
            val change1 = Change(TestMarykModel { string::ref } with "ha new message 1")
            dataStore.execute(TestMarykModel.change(
                keys[1].change(change1)
            ))

            val changeUpdate1 = responses[0].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(keys[1], key)
                assertEquals(changes, listOf(change1))
            }

            // Ignored change because scan starts at later key,
            // next response should be for next change
            dataStore.execute(TestMarykModel.change(
                keys[0].change(
                    Change(TestMarykModel { string::ref } with "ha new message 3")
                )
            ))

            val change2 = Change(TestMarykModel { string::ref } with "ha new message 3")
            dataStore.execute(TestMarykModel.change(
                keys[2].change(change2)
            ))

            val changeUpdate2 = responses[1].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate2).apply {
                assertEquals(keys[2], key)
                assertEquals(changes, listOf(change2))
            }

            dataStore.execute(TestMarykModel.delete(keys[2]))

            val removalUpdate1 = responses[2].await()
            assertType<RemovalUpdate<*, *>>(removalUpdate1).apply {
                assertEquals(keys[2], key)
            }

            val newDataObject = TestMarykModel(
                string = "ha world 6",
                int = 6,
                uint = 23123214u,
                bool = true,
                double = 6968798.37465,
                dateTime = DateTime(1922, 12, 23)
            )

            dataStore.execute(TestMarykModel.add(
                newDataObject
            ))

            val additionUpdate = responses[3].await()
            assertType<AdditionUpdate<TestMarykModel, TestMarykModel.Properties>>(additionUpdate).apply {
                assertEquals(newDataObject, values)
            }
        }
    }

    private fun executeOrderedScanChangesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanChanges(
                startKey = keys[1],
                order = TestMarykModel { int::ref }.ascending(),
                limit = 2u,
                includeStart = false
            ),
            4
        ) { responses ->
            // Order of keys is now: 1, 3, 0, 2, 4
            // Item at key1 is skipped so starts at 3 now

            val change1 = Change(TestMarykModel { string::ref } with "ha new message 1")
            dataStore.execute(TestMarykModel.change(
                keys[3].change(change1)
            ))

            val changeUpdate1 = responses[0].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(keys[3], key)
                assertEquals(changes, listOf(change1))
            }

            // Ignored change because key is not within startKey to limit
            // next response should be for next change
            dataStore.execute(TestMarykModel.change(
                keys[2].change(
                    Change(TestMarykModel { string::ref } with "ha new message 3")
                )
            ))

            val change2 = Change(TestMarykModel { string::ref } with "ha new message 3")
            dataStore.execute(TestMarykModel.change(
                keys[0].change(change2)
            ))

            val changeUpdate2 = responses[1].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate2).apply {
                assertEquals(keys[0], key)
                assertEquals(changes, listOf(change2))
            }

            dataStore.execute(TestMarykModel.delete(keys[0]))

            val removalUpdate1 = responses[2].await()
            assertType<RemovalUpdate<*, *>>(removalUpdate1).apply {
                assertEquals(keys[0], key)
            }

            val addUpdate = responses[3].await()
            assertType<AdditionUpdate<*, *>>(addUpdate).apply {
                assertEquals(keys[2], key)
            }

            val newDataObject = TestMarykModel(
                string = "ha world 6",
                int = 6,
                uint = 23123214u,
                bool = true,
                double = 6968798.37465,
                dateTime = DateTime(1922, 12, 23)
            )

            dataStore.execute(TestMarykModel.add(
                newDataObject
            ))

            // no updates otherwise crash because not enough responses
        }
    }
}
