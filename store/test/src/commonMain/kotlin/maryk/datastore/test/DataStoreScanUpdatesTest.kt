package maryk.datastore.test

import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.changes.change
import maryk.core.query.filters.Exists
import maryk.core.query.orders.Order.Companion.descending
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.lib.time.DateTime
import maryk.test.assertType
import maryk.test.models.TestMarykModel
import maryk.test.models.TestMarykModel.Properties
import maryk.test.runSuspendingTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.expect

val t0 = TestMarykModel(
    string = "ha world 1",
    int = 0,
    uint = 67u,
    bool = true,
    double = 2323.3,
    dateTime = DateTime(1989, 9, 8)
)
val t1 = TestMarykModel(
    string = "ha world 2",
    int = -10,
    uint = 69u,
    bool = false,
    double = 0.1,
    dateTime = DateTime(2001, 4, 2)
)
val t2 = TestMarykModel(
    string = "ha world 3",
    int = 2,
    uint = 1244u,
    bool = true,
    double = 444.0,
    dateTime = DateTime(2005, 11, 30)
)
val t3 = TestMarykModel(
    string = "ha world 4",
    int = -5,
    uint = 52323u,
    bool = false,
    double = 2333.0,
    dateTime = DateTime(2012, 1, 28)
)
val t4 = TestMarykModel(
    string = "ha world 5",
    int = 4,
    uint = 234234u,
    bool = true,
    double = 232523.3,
    dateTime = DateTime(2020, 3, 23)
)

class DataStoreScanUpdatesTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val testKeys = mutableListOf<Key<TestMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE
    private var highestInitVersion = ULong.MIN_VALUE

    override val allTests = mapOf(
        "executeSimpleScanUpdatesRequest" to ::executeSimpleScanUpdatesRequest,
        "executeOrderedScanUpdatesRequest" to ::executeOrderedScanUpdatesRequest,
        "failWithMutableWhereClause" to ::failWithMutableWhereClause,
        "executeScanChangesAsFlowRequest" to ::executeScanChangesAsFlowRequest,
        "executeScanChangesIncludingInitValuesAsFlowRequest" to ::executeScanChangesIncludingInitValuesAsFlowRequest,
        "executeScanChangesAsFlowWithSelectRequest" to ::executeScanChangesAsFlowWithSelectRequest,
        "executeReversedScanChangesAsFlowRequest" to ::executeReversedScanChangesAsFlowRequest,
        "executeOrderedScanChangesAsFlowRequest" to ::executeOrderedScanChangesAsFlowRequest,
        "executeReverseOrderedScanChangesAsFlowRequest" to ::executeReverseOrderedScanChangesAsFlowRequest
    )

    override fun initData() {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                TestMarykModel.add(t0, t1, t2, t3, t4)
            )
            addResponse.statuses.forEach { status ->
                val response = assertType<AddSuccess<TestMarykModel>>(status)
                testKeys.add(response.key)
                if (response.version < lowestVersion) {
                    // Add lowest version for scan test
                    lowestVersion = response.version
                }
                if (response.version > highestInitVersion) {
                    highestInitVersion = response.version
                }
            }
        }
    }

    override fun resetData() {
        runSuspendingTest {
            dataStore.execute(
                TestMarykModel.delete(*testKeys.toTypedArray(), hardDelete = true)
            )
        }
        testKeys.clear()
        lowestVersion = ULong.MAX_VALUE
        highestInitVersion = ULong.MIN_VALUE
    }

    private fun executeSimpleScanUpdatesRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            TestMarykModel.scanUpdates(startKey = testKeys[1])
        )

        expect(5) { scanResponse.updates.size }

        assertType<OrderedKeysUpdate<TestMarykModel, Properties>>(scanResponse.updates[0]).apply {
            assertEquals(listOf(testKeys[1], testKeys[2], testKeys[3], testKeys[4]), keys)
            assertNull(sortingKeys)
            assertEquals(highestInitVersion, version)
        }

        assertType<AdditionUpdate<TestMarykModel, Properties>>(scanResponse.updates[1]).apply {
            assertEquals(testKeys[1], key)
            assertEquals(t1, values)
        }

        assertType<AdditionUpdate<TestMarykModel, Properties>>(scanResponse.updates[4]).apply {
            assertEquals(testKeys[4], key)
            assertEquals(t4, values)
        }
    }

    private fun executeOrderedScanUpdatesRequest() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            TestMarykModel.scanUpdates(
                startKey = testKeys[1],
                order = TestMarykModel { int::ref }.ascending(),
                limit = 2u,
                includeStart = false
            )
        )

        expect(3) { scanResponse.updates.size }

        assertType<OrderedKeysUpdate<TestMarykModel, Properties>>(scanResponse.updates[0]).apply {
            assertEquals(listOf(testKeys[3], testKeys[0]), keys)
            assertEquals(highestInitVersion, version)
        }

        assertType<AdditionUpdate<TestMarykModel, Properties>>(scanResponse.updates[1]).apply {
            assertEquals(testKeys[3], key)
            assertEquals(t3, values)
        }

        assertType<AdditionUpdate<TestMarykModel, Properties>>(scanResponse.updates[2]).apply {
            assertEquals(testKeys[0], key)
            assertEquals(t0, values)
        }
    }

    private fun failWithMutableWhereClause() = runSuspendingTest {
        assertFailsWith<RequestException> {
            dataStore.executeFlow(
                TestMarykModel.getUpdates(testKeys[0], testKeys[1], where = Exists(TestMarykModel { string::ref }))
            )
        }
    }

    private fun executeScanChangesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanUpdates(
                startKey = testKeys[1],
                fromVersion = highestInitVersion + 1uL,
                orderedKeys = listOf(testKeys[0], testKeys[1], testKeys[2], testKeys[4])
            ),
            7
        ) { responses ->
            assertType<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[1], testKeys[2], testKeys[3], testKeys[4]), keys)
                assertEquals(highestInitVersion, version)
            }

            // Based on passed ordered keys to the server
            val removalUpdate0 = responses[1].await()
            assertType<RemovalUpdate<*, *>>(removalUpdate0).apply {
                assertEquals(testKeys[0], key)
                assertEquals(NotInRange, reason)
                assertEquals(highestInitVersion, version)
            }

            // Based on passed ordered keys to the server
            val additionUpdate0 = responses[2].await()
            assertType<AdditionUpdate<*, *>>(additionUpdate0).apply {
                assertEquals(testKeys[3], key)
                assertEquals(highestInitVersion, version)
                assertEquals(2, insertionIndex)
                assertEquals<Values<*, *>>(t3, values)
            }

            val change1 = Change(TestMarykModel { string::ref } with "ha new message 1")
            dataStore.execute(TestMarykModel.change(
                testKeys[1].change(change1)
            ))

            val changeUpdate1 = responses[3].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[1], key)
                assertEquals(listOf(change1), changes)
            }

            // Ignored change because scan starts at later key,
            // next response should be for next change
            dataStore.execute(TestMarykModel.change(
                testKeys[0].change(
                    Change(TestMarykModel { string::ref } with "ha newer message 3")
                )
            ))

            val change2 = Change(TestMarykModel { string::ref } with "ha newer message 3")
            dataStore.execute(TestMarykModel.change(
                testKeys[2].change(change2)
            ))

            val changeUpdate2 = responses[4].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate2).apply {
                assertEquals(testKeys[2], key)
                assertEquals(listOf(change2), changes)
                assertEquals(1, index)
            }

            dataStore.execute(TestMarykModel.delete(testKeys[2], hardDelete = true))

            val removalUpdate1 = responses[5].await()
            assertType<RemovalUpdate<*, *>>(removalUpdate1).apply {
                assertEquals(testKeys[2], key)
                assertEquals(HardDelete, reason)
            }

            val newDataObject = TestMarykModel(
                string = "ha world 6",
                int = 6,
                uint = 23425u,
                bool = true,
                double = 6968798.37465,
                dateTime = DateTime(1922, 12, 23)
            )

            dataStore.execute(TestMarykModel.add(
                newDataObject
            ))

            val additionUpdate = responses[6].await()
            assertType<AdditionUpdate<TestMarykModel, Properties>>(additionUpdate).apply {
                assertEquals(newDataObject, values)
                assertEquals(1, insertionIndex)
                testKeys.add(key)
            }
        }
    }

    private fun executeScanChangesIncludingInitValuesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanUpdates(
                startKey = testKeys[1]
            ),
            6
        ) { responses ->
            assertType<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[1], testKeys[2], testKeys[3], testKeys[4]), keys)
                assertEquals(highestInitVersion, version)
            }

            val prevUpdate1 = responses[1].await()
            assertType<AdditionUpdate<TestMarykModel, Properties>>(prevUpdate1).apply {
                assertEquals(testKeys[1], key)
                assertEquals(t1, values)
                assertEquals(0, insertionIndex)
            }

            responses[2].await()
            responses[3].await()
            // Expect this to be the last added value
            assertType<AdditionUpdate<TestMarykModel, Properties>>(responses[4].await()).apply {
                assertEquals(testKeys[4], key)
                assertEquals(highestInitVersion, this.version)
                assertEquals(3, insertionIndex)
            }

            val change1 = Change(TestMarykModel { string::ref } with "ha new message 1")
            dataStore.execute(TestMarykModel.change(
                testKeys[1].change(change1)
            ))

            val changeUpdate1 = responses[5].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[1], key)
                assertEquals(listOf(change1), changes)
            }
        }
    }

    private fun executeScanChangesAsFlowWithSelectRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanUpdates(
                startKey = testKeys[1],
                select = TestMarykModel.graph {
                    listOf(string)
                },
                fromVersion = highestInitVersion + 1uL
            ),
            2
        ) { responses ->
            assertType<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[1], testKeys[2], testKeys[3], testKeys[4]), keys)
                assertEquals(highestInitVersion, version)
            }

            val change1 = Change(
                TestMarykModel { string::ref } with "ha new message 1",
                TestMarykModel { double::ref } with 1.5
            )
            dataStore.execute(TestMarykModel.change(
                testKeys[1].change(change1)
            ))

            val changeUpdate1 = responses[1].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[1], key)
                assertEquals(listOf(
                    Change(TestMarykModel { string::ref } with "ha new message 1"),
                    IndexChange(listOf(
                        IndexUpdate(
                            index = Bytes("CyE"),
                            indexKey = Bytes("wAf///////4IAAAARQAAAQ"),
                            previousIndexKey = Bytes("wEZmZmZmZmQIAAAARQAAAQ")
                        )
                    ))
                ), changes)
            }

            dataStore.execute(TestMarykModel.change(
                testKeys[1].change(
                    Change(
                        TestMarykModel { double::ref } with 2.5
                    )
                )
            ))

            // No update should be handled, otherwise crashes since there is only 1 response slot and it has been used
        }
    }

    private fun executeReversedScanChangesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanUpdates(
                startKey = testKeys[3],
                order = descending,
                fromVersion = highestInitVersion + 1uL
            ),
            5
        ) { responses ->
            assertType<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[3], testKeys[2], testKeys[1], testKeys[0]), keys)
                assertEquals(highestInitVersion, version)
            }

            val change1 = Change(TestMarykModel { string::ref } with "ha new message 1")
            dataStore.execute(TestMarykModel.change(
                testKeys[3].change(change1)
            ))

            val changeUpdate1 = responses[1].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[3], key)
                assertEquals(listOf(change1), changes)
                assertEquals(0, index)
            }

            // Ignored change because scan starts at earlier key,
            // next response should be for next change
            dataStore.execute(TestMarykModel.change(
                testKeys[4].change(
                    Change(TestMarykModel { string::ref } with "ha new message 3")
                )
            ))

            val change2 = Change(TestMarykModel { string::ref } with "ha new message 3")
            dataStore.execute(TestMarykModel.change(
                testKeys[2].change(change2)
            ))

            val changeUpdate2 = responses[2].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate2).apply {
                assertEquals(testKeys[2], key)
                assertEquals(listOf(change2), changes)
                assertEquals(1, index)
            }

            dataStore.execute(TestMarykModel.delete(testKeys[2], hardDelete = true))

            val removalUpdate1 = responses[3].await()
            assertType<RemovalUpdate<*, *>>(removalUpdate1).apply {
                assertEquals(testKeys[2], key)
                assertEquals(HardDelete, reason)
            }

            val newDataObject = TestMarykModel(
                string = "ha world 6",
                int = -20,
                uint = 1u,
                bool = true,
                double = 6968798.37465,
                dateTime = DateTime(1922, 12, 23)
            )

            dataStore.execute(TestMarykModel.add(
                newDataObject
            )).also {
                testKeys.add((it.statuses[0] as AddSuccess<TestMarykModel>).key)
            }

            val additionUpdate = responses[4].await()
            assertType<AdditionUpdate<TestMarykModel, Properties>>(additionUpdate).apply {
                assertEquals(newDataObject, values)
                assertEquals(3, insertionIndex)
            }
        }
    }

    private fun executeOrderedScanChangesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanUpdates(
                startKey = testKeys[1],
                order = TestMarykModel { int::ref }.ascending(),
                limit = 2u,
                includeStart = false,
                fromVersion = highestInitVersion + 1uL
            ),
            12
        ) { responses ->
            // Order of keys is now: 1, 3, 0, 2, 4
            // Item at key1 is skipped so starts at 3 now
            assertType<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[3], testKeys[0]), keys)
                assertEquals(highestInitVersion, version)
            }

            val change1 = Change(TestMarykModel { string::ref } with "ha new message 1")
            dataStore.execute(TestMarykModel.change(
                testKeys[3].change(change1)
            ))

            val changeUpdate1 = responses[1].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[3], key)
                assertEquals(listOf(change1), changes)
            }

            // Ignored change because key is not within startKey to limit
            // next response should be for next change
            dataStore.execute(TestMarykModel.change(
                testKeys[2].change(
                    Change(TestMarykModel { string::ref } with "ha new message 3")
                )
            ))

            val change2 = Change(TestMarykModel { string::ref } with "ha new new message 3")
            dataStore.execute(TestMarykModel.change(
                testKeys[0].change(change2)
            ))

            val changeUpdate2 = responses[2].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate2).apply {
                assertEquals(testKeys[0], key)
                assertEquals(listOf(change2), changes)
            }

            dataStore.execute(TestMarykModel.delete(testKeys[0]))

            val removalUpdate1 = responses[3].await()
            assertType<RemovalUpdate<*, *>>(removalUpdate1).apply {
                assertEquals(testKeys[0], key)
                assertEquals(SoftDelete, reason)
            }

            val addUpdate = responses[4].await()
            assertType<AdditionUpdate<*, *>>(addUpdate).apply {
                assertEquals(testKeys[2], key)
            }

            val newDataObject = TestMarykModel(
                string = "ha world 6",
                int = 6,
                uint = 23133214u,
                bool = true,
                double = 6968798.37465,
                dateTime = DateTime(1922, 12, 23)
            )

            dataStore.execute(TestMarykModel.add(
                newDataObject
            )).also {
                testKeys.add((it.statuses[0] as AddSuccess<TestMarykModel>).key)
            }

            // no updates because is outside the limit otherwise next one will not match

            val newDataObject2 = TestMarykModel(
                string = "ha new world",
                int = -2,
                uint = 4321u,
                bool = false,
                double = 1.1,
                dateTime = DateTime(1901, 1, 2)
            )

            // New object is added within range in already full list so we expect an add and a delete
            dataStore.execute(TestMarykModel.add(
                newDataObject2
            ))

            val additionUpdate = responses[5].await()
            assertType<AdditionUpdate<TestMarykModel, Properties>>(additionUpdate).apply {
                assertEquals<Values<*, *>>(values, newDataObject2)
                assertEquals(1, insertionIndex)
                testKeys.add(key)
            }

            val removalUpdate2 = responses[6].await()
            assertType<RemovalUpdate<TestMarykModel, Properties>>(removalUpdate2).apply {
                assertEquals(testKeys[2], key)
                assertEquals(NotInRange, reason)
            }

            // Change value which changes order
            val change3 = Change(TestMarykModel { int::ref } with 0)
            dataStore.execute(TestMarykModel.change(
                testKeys[3].change(change3)
            ))

            val changeUpdate3 = responses[7].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate3).apply {
                assertEquals(testKeys[3], key)
                assertEquals(listOf(
                    change3,
                    IndexChange(listOf(
                        IndexUpdate(
                            index = Bytes("BAILKQIKOQIKEQ"),
                            indexKey = Bytes("f///sNzFfwABgAAAAAAAzGMAAAEAAAA"),
                            previousIndexKey = Bytes("f///sNzFfwABf///+wAAzGMAAAEAAAA")
                        ),
                        IndexUpdate(
                            index = Bytes("ChE"),
                            indexKey = Bytes("gAAAAAQAAMxjAAAB"),
                            previousIndexKey = Bytes("f///+wQAAMxjAAAB")
                        )
                    ))
                ), changes)
                assertEquals(1, index)
            }

            // Move item out of range by changing value on which index is determined

            val change4 = Change(TestMarykModel { int::ref } with 5)
            dataStore.execute(TestMarykModel.change(
                testKeys[3].change(change4)
            ))

            val removalUpdate3 = responses[8].await()
            assertType<RemovalUpdate<*, *>>(removalUpdate3).apply {
                assertEquals(testKeys[3], key)
                assertEquals(NotInRange, reason)
            }

            val additionUpdate2 = responses[9].await()
            assertType<AdditionUpdate<*, *>>(additionUpdate2).apply {
                assertEquals(testKeys[2], key)
                assertEquals(1, insertionIndex)
            }

            // Move item back to its old position

            val change5 = Change(TestMarykModel { int::ref } with -1)
            dataStore.execute(TestMarykModel.change(
                testKeys[3].change(change5)
            ))

            val removalUpdate4 = responses[10].await()
            assertType<RemovalUpdate<*, *>>(removalUpdate4).apply {
                assertEquals(testKeys[2], key)
                assertEquals(NotInRange, reason)
            }

            val additionUpdate3 = responses[11].await()
            assertType<AdditionUpdate<*, *>>(additionUpdate3).apply {
                assertEquals(testKeys[3], key)
                assertEquals(1, insertionIndex)
            }
        }
    }

    private fun executeReverseOrderedScanChangesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanUpdates(
                startKey = testKeys[4],
                order = TestMarykModel { int::ref }.descending(),
                limit = 2u,
                includeStart = false,
                fromVersion = highestInitVersion + 1uL
            ),
            8
        ) { responses ->
            // Order of keys is now: 4, 2, 0, 3, 1
            // Item at key4 is skipped so starts at 2 now
            assertType<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[2], testKeys[0]), keys)
                assertEquals(highestInitVersion, version)
            }

            val change1 = Change(TestMarykModel { string::ref } with "ha new message 1")
            dataStore.execute(TestMarykModel.change(
                testKeys[2].change(change1)
            ))

            val changeUpdate1 = responses[1].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[2], key)
                assertEquals(listOf(change1), changes)
            }

            // Ignored change because key is not within startKey to limit
            // next response should be for next change
            dataStore.execute(TestMarykModel.change(
                testKeys[4].change(
                    Change(TestMarykModel { string::ref } with "ha new message 3")
                )
            ))

            val change2 = Change(TestMarykModel { string::ref } with "ha new message 3")
            dataStore.execute(TestMarykModel.change(
                testKeys[0].change(change2)
            ))

            val changeUpdate2 = responses[2].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate2).apply {
                assertEquals(testKeys[0], key)
                assertEquals(listOf(change2), changes)
            }

            dataStore.execute(TestMarykModel.delete(testKeys[0]))

            val removalUpdate1 = responses[3].await()
            assertType<RemovalUpdate<*, *>>(removalUpdate1).apply {
                assertEquals(testKeys[0], key)
                assertEquals(SoftDelete, reason)
            }

            val addUpdate = responses[4].await()
            assertType<AdditionUpdate<*, *>>(addUpdate).apply {
                assertEquals(testKeys[3], key)
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
            )).also {
                testKeys.add((it.statuses[0] as AddSuccess<TestMarykModel>).key)
            }

            // no updates because is outside the limit otherwise next one will not match

            val newDataObject2 = TestMarykModel(
                string = "ha new world",
                int = -1,
                uint = 4321u,
                bool = false,
                double = 1.1,
                dateTime = DateTime(1901, 1, 2)
            )

            // New object is added within range in already full list so we expect an add and a delete
            dataStore.execute(TestMarykModel.add(
                newDataObject2
            ))

            val additionUpdate = responses[5].await()
            assertType<AdditionUpdate<TestMarykModel, Properties>>(additionUpdate).apply {
                assertEquals<Values<*, *>>(newDataObject2, values)
                assertEquals(1, insertionIndex)
                testKeys.add(key)
            }

            val removalUpdate2 = responses[6].await()
            assertType<RemovalUpdate<TestMarykModel, Properties>>(removalUpdate2).apply {
                assertEquals(testKeys[2], key)
                assertEquals(NotInRange, reason)
            }

            // Change value which changes order
            val change3 = Change(TestMarykModel { int::ref } with -3)
            dataStore.execute(TestMarykModel.change(
                testKeys[3].change(change3)
            ))

            val changeUpdate3 = responses[7].await()
            assertType<ChangeUpdate<*, *>>(changeUpdate3).apply {
                assertEquals(testKeys[3], key)
                assertEquals(listOf(
                    change3,
                    IndexChange(listOf(
                        IndexUpdate(
                            index = Bytes("BAILKQIKOQIKEQ"),
                            indexKey = Bytes("f///sNzFfwABf////QAAzGMAAAEAAAA"),
                            previousIndexKey = Bytes("f///sNzFfwABf///+wAAzGMAAAEAAAA")
                        ),
                        IndexUpdate(
                            index = Bytes("ChE"),
                            indexKey = Bytes("f////QQAAMxjAAAB"),
                            previousIndexKey = Bytes("f///+wQAAMxjAAAB")
                        )
                    ))
                ), changes)
                assertEquals(1, index)
            }
        }
    }
}
