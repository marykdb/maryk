package maryk.datastore.test

import kotlinx.datetime.LocalDateTime
import maryk.core.models.RootDataModel
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Not
import maryk.core.query.orders.Order.Companion.descending
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.test.models.TestMarykModel
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.expect

val t0 = TestMarykModel(
    string = "ha world 1",
    int = 0,
    uint = 67u,
    bool = true,
    double = 2323.3,
    dateTime = LocalDateTime(1989, 9, 8, 0, 0)
)
val t1 = TestMarykModel(
    string = "ha world 2",
    int = -10,
    uint = 69u,
    bool = false,
    double = 0.1,
    dateTime = LocalDateTime(2001, 4, 2, 0, 0)
)
val t2 = TestMarykModel(
    string = "ha world 3",
    int = 2,
    uint = 1244u,
    bool = true,
    double = 444.0,
    dateTime = LocalDateTime(2005, 11, 30, 0, 0)
)
val t3 = TestMarykModel(
    string = "ha world 4",
    int = -5,
    uint = 52323u,
    bool = false,
    double = 2333.0,
    dateTime = LocalDateTime(2012, 1, 28, 0, 0)
)
val t4 = TestMarykModel(
    string = "ha world 5",
    int = 4,
    uint = 234234u,
    bool = true,
    double = 232523.3,
    dateTime = LocalDateTime(2020, 3, 23, 0, 0)
)

class DataStoreScanUpdatesAndFlowTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val testKeys = mutableListOf<Key<RootDataModel<TestMarykModel>>>()
    private var lowestVersion = ULong.MAX_VALUE
    private var highestInitVersion = ULong.MIN_VALUE

    override val allTests = mapOf(
        "executeSimpleScanUpdatesRequest" to ::executeSimpleScanUpdatesRequest,
        "executeOrderedScanUpdatesRequest" to ::executeOrderedScanUpdatesRequest,
        "executeScanValuesAsFlowRequest" to ::executeScanValuesAsFlowRequest,
        "executeScanChangesAsFlowRequest" to ::executeScanChangesAsFlowRequest,
        "executeScanUpdatesAsFlowRequest" to ::executeScanUpdatesAsFlowRequest,
        "executeScanUpdatesAsFlowWithMutableWhereRequest" to ::executeScanUpdatesAsFlowWithMutableWhereRequest,
        "executeScanUpdatesIncludingInitValuesAsFlowRequest" to ::executeScanUpdatesIncludingInitValuesAsFlowRequest,
        "executeScanUpdatesAsFlowWithSelectRequest" to ::executeScanUpdatesAsFlowWithSelectRequest,
        "executeReversedScanUpdatesAsFlowRequest" to ::executeReversedScanUpdatesAsFlowRequest,
        "executeOrderedScanUpdatesAsFlowRequest" to ::executeOrderedScanUpdatesAsFlowRequest,
        "executeReverseOrderedScanUpdatesAsFlowRequest" to ::executeReverseOrderedScanUpdatesAsFlowRequest
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            TestMarykModel.add(t0, t1, t2, t3, t4)
        )
        addResponse.statuses.forEach { status ->
            val response = assertIs<AddSuccess<RootDataModel<TestMarykModel>>>(status)
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

    override suspend fun resetData() {
        dataStore.execute(
            TestMarykModel.Model.delete(*testKeys.toTypedArray(), hardDelete = true)
        )
        testKeys.clear()
        lowestVersion = ULong.MAX_VALUE
        highestInitVersion = ULong.MIN_VALUE
    }

    private suspend fun executeSimpleScanUpdatesRequest() {
        val scanResponse = dataStore.execute(
            TestMarykModel.scanUpdates(startKey = testKeys[1])
        )

        expect(5) { scanResponse.updates.size }

        assertIs<OrderedKeysUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(scanResponse.updates[0]).apply {
            assertEquals(listOf(testKeys[1], testKeys[2], testKeys[3], testKeys[4]), keys)
            assertNull(sortingKeys)
            assertEquals(highestInitVersion, version)
        }

        assertIs<AdditionUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(scanResponse.updates[1]).apply {
            assertEquals(testKeys[1], key)
            assertEquals(t1, values)
        }

        assertIs<AdditionUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(scanResponse.updates[4]).apply {
            assertEquals(testKeys[4], key)
            assertEquals(t4, values)
        }
    }

    private suspend fun executeOrderedScanUpdatesRequest() {
        val scanResponse = dataStore.execute(
            TestMarykModel.scanUpdates(
                startKey = testKeys[1],
                order = TestMarykModel { int::ref }.ascending(),
                limit = 2u,
                includeStart = false
            )
        )

        expect(3) { scanResponse.updates.size }

        assertIs<OrderedKeysUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(scanResponse.updates[0]).apply {
            assertEquals(listOf(testKeys[3], testKeys[0]), keys)
            assertEquals(highestInitVersion, version)
        }

        assertIs<AdditionUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(scanResponse.updates[1]).apply {
            assertEquals(testKeys[3], key)
            assertEquals(t3, values)
        }

        assertIs<AdditionUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(scanResponse.updates[2]).apply {
            assertEquals(testKeys[0], key)
            assertEquals(t0, values)
        }
    }

    private suspend fun executeScanValuesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scan(
                startKey = testKeys[1]
            ),
            2
        ) { responses ->
            assertIs<InitialValuesUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[1], testKeys[2], testKeys[3], testKeys[4]), values.map { it.key })
                assertEquals(highestInitVersion, version)
            }

            val change = Change(TestMarykModel { string::ref } with "ha new message for values")
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[1].change(change)
            ))

            val changeUpdate = responses[1].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate).apply {
                assertEquals(testKeys[1], key)
                assertEquals(listOf(change), changes)
            }
        }
    }

    private suspend fun executeScanChangesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanChanges(
                startKey = testKeys[1]
            ),
            2
        ) { responses ->
            assertIs<InitialChangesUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[1], testKeys[2], testKeys[3], testKeys[4]), changes.map { it.key })
                assertEquals(highestInitVersion, version)
            }

            val change1 = Change(TestMarykModel { string::ref } with "ha new message for change")
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[1].change(change1)
            ))

            val changeUpdate1 = responses[1].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[1], key)
                assertEquals(listOf(change1), changes)
            }
        }
    }

    private suspend fun executeScanUpdatesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanUpdates(
                startKey = testKeys[1],
                fromVersion = highestInitVersion + 1uL,
                orderedKeys = listOf(testKeys[0], testKeys[1], testKeys[2], testKeys[4])
            ),
            7
        ) { responses ->
            assertIs<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[1], testKeys[2], testKeys[3], testKeys[4]), keys)
                assertEquals(highestInitVersion, version)
            }

            // Based on passed ordered keys to the server
            val removalUpdate0 = responses[1].await()
            assertIs<RemovalUpdate<*, *>>(removalUpdate0).apply {
                assertEquals(testKeys[0], key)
                assertEquals(NotInRange, reason)
                assertEquals(highestInitVersion, version)
            }

            // Based on passed ordered keys to the server
            val additionUpdate0 = responses[2].await()
            assertIs<AdditionUpdate<*, *>>(additionUpdate0).apply {
                assertEquals(testKeys[3], key)
                assertEquals(highestInitVersion, version)
                assertEquals(2, insertionIndex)
                assertEquals<Values<*, *>>(t3, values)
            }

            val change1 = Change(TestMarykModel { string::ref } with "ha new message 1")
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[1].change(change1)
            ))

            val changeUpdate1 = responses[3].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[1], key)
                assertEquals(listOf(change1), changes)
            }

            // Ignored change because scan starts at later key,
            // next response should be for next change
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[0].change(
                    Change(TestMarykModel { string::ref } with "ha newer message 3")
                )
            ))

            val change2 = Change(TestMarykModel { string::ref } with "ha newer message 3")
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[2].change(change2)
            ))

            val changeUpdate2 = responses[4].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate2).apply {
                assertEquals(testKeys[2], key)
                assertEquals(listOf(change2), changes)
                assertEquals(1, index)
            }

            dataStore.execute(TestMarykModel.Model.delete(testKeys[2], hardDelete = true))

            val removalUpdate1 = responses[5].await()
            assertIs<RemovalUpdate<*, *>>(removalUpdate1).apply {
                assertEquals(testKeys[2], key)
                assertEquals(HardDelete, reason)
            }

            val newDataObject = TestMarykModel(
                string = "ha world 6",
                int = 6,
                uint = 23425u,
                bool = true,
                double = 6968798.37465,
                dateTime = LocalDateTime(1922, 12, 23, 0, 0)
            )

            dataStore.execute(TestMarykModel.add(
                newDataObject
            ))

            val additionUpdate = responses[6].await()
            assertIs<AdditionUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(additionUpdate).apply {
                assertEquals(newDataObject, values)
                assertEquals(1, insertionIndex)
                testKeys.add(key)
            }
        }
    }

    private suspend fun executeScanUpdatesAsFlowWithMutableWhereRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanUpdates(
                startKey = testKeys[1],
                where = Not(Equals(TestMarykModel { string::ref } with "ha filtered message")),
                fromVersion = highestInitVersion + 1uL
            ),
            2
        ) { responses ->
            assertIs<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[1], testKeys[2], testKeys[3], testKeys[4]), keys)
                assertEquals(highestInitVersion, version)
            }

            val change1 = Change(TestMarykModel { string::ref } with "ha filtered message")
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[1].change(change1)
            ))

            val changeUpdate1 = responses[1].await()
            assertIs<RemovalUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[1], key)
                assertEquals(NotInRange, reason)
            }
        }
    }

    private suspend fun executeScanUpdatesIncludingInitValuesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanUpdates(
                startKey = testKeys[1]
            ),
            6
        ) { responses ->
            assertIs<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[1], testKeys[2], testKeys[3], testKeys[4]), keys)
                assertEquals(highestInitVersion, version)
            }

            val prevUpdate1 = responses[1].await()
            assertIs<AdditionUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(prevUpdate1).apply {
                assertEquals(testKeys[1], key)
                assertEquals(t1, values)
                assertEquals(0, insertionIndex)
            }

            responses[2].await()
            responses[3].await()
            // Expect this to be the last added value
            assertIs<AdditionUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(responses[4].await()).apply {
                assertEquals(testKeys[4], key)
                assertEquals(highestInitVersion, this.version)
                assertEquals(3, insertionIndex)
            }

            val change1 = Change(TestMarykModel { string::ref } with "ha new message 1")
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[1].change(change1)
            ))

            val changeUpdate1 = responses[5].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[1], key)
                assertEquals(listOf(change1), changes)
            }
        }
    }

    private suspend fun executeScanUpdatesAsFlowWithSelectRequest() {
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
            assertIs<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[1], testKeys[2], testKeys[3], testKeys[4]), keys)
                assertEquals(highestInitVersion, version)
            }

            val change1 = Change(
                TestMarykModel { string::ref } with "ha new message 1",
                TestMarykModel { double::ref } with 1.5
            )
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[1].change(change1)
            ))

            val changeUpdate1 = responses[1].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate1).apply {
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

            dataStore.execute(TestMarykModel.Model.change(
                testKeys[1].change(
                    Change(
                        TestMarykModel { double::ref } with 2.5
                    )
                )
            ))

            // No update should be handled, otherwise crashes since there is only 1 response slot, and it has been used
        }
    }

    private suspend fun executeReversedScanUpdatesAsFlowRequest() {
        updateListenerTester(
            dataStore,
            TestMarykModel.scanUpdates(
                startKey = testKeys[3],
                order = descending,
                fromVersion = highestInitVersion + 1uL
            ),
            5
        ) { responses ->
            assertIs<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[3], testKeys[2], testKeys[1], testKeys[0]), keys)
                assertEquals(highestInitVersion, version)
            }

            val change1 = Change(TestMarykModel { string::ref } with "ha new message 1")
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[3].change(change1)
            ))

            val changeUpdate1 = responses[1].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[3], key)
                assertEquals(listOf(change1), changes)
                assertEquals(0, index)
            }

            // Ignored change because scan starts at earlier key,
            // next response should be for next change
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[4].change(
                    Change(TestMarykModel { string::ref } with "ha new message 3")
                )
            ))

            val change2 = Change(TestMarykModel { string::ref } with "ha new message 3")
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[2].change(change2)
            ))

            val changeUpdate2 = responses[2].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate2).apply {
                assertEquals(testKeys[2], key)
                assertEquals(listOf(change2), changes)
                assertEquals(1, index)
            }

            dataStore.execute(TestMarykModel.Model.delete(testKeys[2], hardDelete = true))

            val removalUpdate1 = responses[3].await()
            assertIs<RemovalUpdate<*, *>>(removalUpdate1).apply {
                assertEquals(testKeys[2], key)
                assertEquals(HardDelete, reason)
            }

            val newDataObject = TestMarykModel(
                string = "ha world 6",
                int = -20,
                uint = 1u,
                bool = true,
                double = 6968798.37465,
                dateTime = LocalDateTime(1922, 12, 23, 0, 0)
            )

            dataStore.execute(TestMarykModel.add(
                newDataObject
            )).also {
                testKeys.add((it.statuses[0] as AddSuccess<RootDataModel<TestMarykModel>>).key)
            }

            val additionUpdate = responses[4].await()
            assertIs<AdditionUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(additionUpdate).apply {
                assertEquals(newDataObject, values)
                assertEquals(3, insertionIndex)
            }
        }
    }

    private suspend fun executeOrderedScanUpdatesAsFlowRequest() {
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
            assertIs<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[3], testKeys[0]), keys)
                assertEquals(highestInitVersion, version)
            }

            val change1 = Change(TestMarykModel { string::ref } with "ha new message 1")
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[3].change(change1)
            ))

            val changeUpdate1 = responses[1].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[3], key)
                assertEquals(listOf(change1), changes)
            }

            // Ignored change because key is not within startKey to limit
            // next response should be for next change
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[2].change(
                    Change(TestMarykModel { string::ref } with "ha new message 3")
                )
            ))

            val change2 = Change(TestMarykModel { string::ref } with "ha new new message 3")
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[0].change(change2)
            ))

            val changeUpdate2 = responses[2].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate2).apply {
                assertEquals(testKeys[0], key)
                assertEquals(listOf(change2), changes)
            }

            dataStore.execute(TestMarykModel.Model.delete(testKeys[0]))

            val removalUpdate1 = responses[3].await()
            assertIs<RemovalUpdate<*, *>>(removalUpdate1).apply {
                assertEquals(testKeys[0], key)
                assertEquals(SoftDelete, reason)
            }

            val addUpdate = responses[4].await()
            assertIs<AdditionUpdate<*, *>>(addUpdate).apply {
                assertEquals(testKeys[2], key)
            }

            val newDataObject = TestMarykModel(
                string = "ha world 6",
                int = 6,
                uint = 23133214u,
                bool = true,
                double = 6968798.37465,
                dateTime = LocalDateTime(1922, 12, 23, 0, 0)
            )

            dataStore.execute(TestMarykModel.add(
                newDataObject
            )).also {
                testKeys.add((it.statuses[0] as AddSuccess<RootDataModel<TestMarykModel>>).key)
            }

            // no updates because is outside the limit otherwise next one will not match

            val newDataObject2 = TestMarykModel(
                string = "ha new world",
                int = -2,
                uint = 4321u,
                bool = false,
                double = 1.1,
                dateTime = LocalDateTime(1901, 1, 2, 0, 0)
            )

            // New object is added within range in already full list, so we expect an add and a delete
            dataStore.execute(TestMarykModel.add(
                newDataObject2
            ))

            val additionUpdate = responses[5].await()
            assertIs<AdditionUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(additionUpdate).apply {
                assertEquals<Values<*, *>>(values, newDataObject2)
                assertEquals(1, insertionIndex)
                testKeys.add(key)
            }

            val removalUpdate2 = responses[6].await()
            assertIs<RemovalUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(removalUpdate2).apply {
                assertEquals(testKeys[2], key)
                assertEquals(NotInRange, reason)
            }

            // Change value which changes order
            val change3 = Change(TestMarykModel { int::ref } with 0)
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[3].change(change3)
            ))

            val changeUpdate3 = responses[7].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate3).apply {
                assertEquals(testKeys[3], key)
                assertEquals(listOf(
                    change3,
                    IndexChange(listOf(
                        IndexUpdate(
                            index = Bytes("BAILKQIKOQIKEQ"),
                            indexKey = Bytes("f///sNzFfwABgAAAAAQCBwAAzGMAAAE"),
                            previousIndexKey = Bytes("f///sNzFfwABf///+wQCBwAAzGMAAAE")
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
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[3].change(change4)
            ))

            val removalUpdate3 = responses[8].await()
            assertIs<RemovalUpdate<*, *>>(removalUpdate3).apply {
                assertEquals(testKeys[3], key)
                assertEquals(NotInRange, reason)
            }

            val additionUpdate2 = responses[9].await()
            assertIs<AdditionUpdate<*, *>>(additionUpdate2).apply {
                assertEquals(testKeys[2], key)
                assertEquals(1, insertionIndex)
            }

            // Move item back to its old position

            val change5 = Change(TestMarykModel { int::ref } with -1)
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[3].change(change5)
            ))

            val removalUpdate4 = responses[10].await()
            assertIs<RemovalUpdate<*, *>>(removalUpdate4).apply {
                assertEquals(testKeys[2], key)
                assertEquals(NotInRange, reason)
            }

            val additionUpdate3 = responses[11].await()
            assertIs<AdditionUpdate<*, *>>(additionUpdate3).apply {
                assertEquals(testKeys[3], key)
                assertEquals(1, insertionIndex)
            }
        }
    }

    private suspend fun executeReverseOrderedScanUpdatesAsFlowRequest() {
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
            assertIs<OrderedKeysUpdate<*, *>>(responses[0].await()).apply {
                assertEquals(listOf(testKeys[2], testKeys[0]), keys)
                assertEquals(highestInitVersion, version)
            }

            val change1 = Change(TestMarykModel { string::ref } with "ha new message 1")
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[2].change(change1)
            ))

            val changeUpdate1 = responses[1].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate1).apply {
                assertEquals(testKeys[2], key)
                assertEquals(listOf(change1), changes)
            }

            // Ignored change because key is not within startKey to limit
            // next response should be for next change
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[4].change(
                    Change(TestMarykModel { string::ref } with "ha new message 3")
                )
            ))

            val change2 = Change(TestMarykModel { string::ref } with "ha new message 3")
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[0].change(change2)
            ))

            val changeUpdate2 = responses[2].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate2).apply {
                assertEquals(testKeys[0], key)
                assertEquals(listOf(change2), changes)
            }

            dataStore.execute(TestMarykModel.Model.delete(testKeys[0]))

            val removalUpdate1 = responses[3].await()
            assertIs<RemovalUpdate<*, *>>(removalUpdate1).apply {
                assertEquals(testKeys[0], key)
                assertEquals(SoftDelete, reason)
            }

            val addUpdate = responses[4].await()
            assertIs<AdditionUpdate<*, *>>(addUpdate).apply {
                assertEquals(testKeys[3], key)
            }

            val newDataObject = TestMarykModel(
                string = "ha world 6",
                int = 6,
                uint = 23123214u,
                bool = true,
                double = 6968798.37465,
                dateTime = LocalDateTime(1922, 12, 23, 0, 0)
            )

            dataStore.execute(TestMarykModel.add(
                newDataObject
            )).also {
                testKeys.add((it.statuses[0] as AddSuccess<RootDataModel<TestMarykModel>>).key)
            }

            // no updates because is outside the limit otherwise next one will not match

            val newDataObject2 = TestMarykModel(
                string = "ha new world",
                int = -1,
                uint = 4321u,
                bool = false,
                double = 1.1,
                dateTime = LocalDateTime(1901, 1, 2, 0, 0)
            )

            // New object is added within range in already full list, so we expect an add and a delete
            dataStore.execute(TestMarykModel.add(
                newDataObject2
            ))

            val additionUpdate = responses[5].await()
            assertIs<AdditionUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(additionUpdate).apply {
                assertEquals<Values<*, *>>(newDataObject2, values)
                assertEquals(1, insertionIndex)
                testKeys.add(key)
            }

            val removalUpdate2 = responses[6].await()
            assertIs<RemovalUpdate<RootDataModel<TestMarykModel>, TestMarykModel>>(removalUpdate2).apply {
                assertEquals(testKeys[2], key)
                assertEquals(NotInRange, reason)
            }

            // Change value which changes order
            val change3 = Change(TestMarykModel { int::ref } with -3)
            dataStore.execute(TestMarykModel.Model.change(
                testKeys[3].change(change3)
            ))

            val changeUpdate3 = responses[7].await()
            assertIs<ChangeUpdate<*, *>>(changeUpdate3).apply {
                assertEquals(testKeys[3], key)
                assertEquals(listOf(
                    change3,
                    IndexChange(listOf(
                        IndexUpdate(
                            index = Bytes("BAILKQIKOQIKEQ"),
                            indexKey = Bytes("f///sNzFfwABf////QQCBwAAzGMAAAE"),
                            previousIndexKey = Bytes("f///sNzFfwABf///+wQCBwAAzGMAAAE")
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
