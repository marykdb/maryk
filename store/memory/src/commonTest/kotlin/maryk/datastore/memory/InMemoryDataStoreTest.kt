package maryk.datastore.memory

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDateTime
import maryk.core.exceptions.StorageException
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.index.UUIDv4Key
import maryk.core.properties.definitions.number
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scanUpdates
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.change
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt32
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runDataStoreTests
import maryk.datastore.test.runDataStoreTestsIsolated
import maryk.datastore.test.UniqueModel
import maryk.test.models.AnyValueIncMapIndexModel
import maryk.test.models.AnyValueSetIndexModel
import maryk.test.models.Log
import maryk.test.models.Severity.INFO
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class InMemoryDataStoreTest {
    @Test
    fun uncollectedFlowDoesNotBlockWritesOrLaterListeners() = runTest(timeout = 10.seconds) {
        val dataStore = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        try {
            dataStore.executeFlow(SimpleMarykModel.scan(allowTableScan = true))

            var completedWrites = 0
            try {
                withContext(Dispatchers.Default.limitedParallelism(1)) {
                    withTimeout(5.seconds) {
                        repeat(140) { index ->
                            dataStore.execute(
                                SimpleMarykModel.add(
                                    SimpleMarykModel.create {
                                        value with "value-$index"
                                    }
                                )
                            )
                            completedWrites++
                        }
                    }
                }
            } catch (error: Throwable) {
                throw AssertionError("Only $completedWrites writes completed before the update pipeline stalled", error)
            }

            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(1.seconds) {
                    dataStore.executeFlow(SimpleMarykModel.scan(allowTableScan = true))
                }
            }
            dataStore.closeAllListeners()
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun testDataStore() = runTest(timeout = 3.minutes) {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        try {
            runDataStoreTests(dataStore)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun testDataStoreWithKeepAllVersions() = runTest(timeout = 3.minutes) {
        runDataStoreTestsIsolated(createDataStore = {
            InMemoryDataStore.open(keepAllVersions = true, dataModelsById = dataModelsForTests)
        })
    }

    @Test
    fun testDataStoreWithUpdateHistoryIndex() = runTest(timeout = 3.minutes) {
        runDataStoreTestsIsolated(
            createDataStore = {
                InMemoryDataStore.open(
                    keepAllVersions = true,
                    keepUpdateHistoryIndex = true,
                    dataModelsById = dataModelsForTests
                )
            },
            runOnlyTests = setOf(
                "executeSimpleScanUpdatesRequestWithUpdateHistoryIndex",
                "executeHistoryStyleScanUpdatesRequestFallsBackWithoutUpdateHistoryIndex",
                "executeScanValuesAsFlowRequestWithUpdateHistoryIndexRefill",
                "executeScanUpdateHistoryReturnsVersionOrderedEntries",
                "executeScanUpdateHistoryCanIncludeSoftDeleteAtHistoricVersion",
                "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndex",
                "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexTracksNewTopKey",
                "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexStartKey",
                "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexRefillsAfterDeletion"
            )
        )
    }

    @Test
    fun scanOnTypeIndexRegression() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        try {
            runDataStoreTests(dataStore, "executeScanOnTypeIndexRequest")
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun changeUpdatesIncMapRefToAnyIndexRegression() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        try {
            runDataStoreTests(dataStore, "executeChangeUpdatesIncMapRefToAnyIndexRequest")
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun exclusiveStartOnMaxIndexValueDoesNotReemitStartRecord() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(dataModelsById = mapOf(1u to MaxNumberScanModel))
        try {
            val addResponse = dataStore.execute(
                MaxNumberScanModel.add(
                    MaxNumberScanModel.create {
                        number with UInt.MAX_VALUE
                    }
                )
            )
            val key = (addResponse.statuses.single() as AddSuccess<MaxNumberScanModel>).key

            val scanResponse = dataStore.execute(
                MaxNumberScanModel.scan(
                    where = Equals(MaxNumberScanModel { number::ref } with UInt.MAX_VALUE),
                    order = Orders(MaxNumberScanModel { number::ref }.ascending()),
                    startKey = key,
                    includeStart = false
                )
            )

            assertEquals(0, scanResponse.values.size)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun exclusiveStartOnMaxIndexValueWithMaxKeyDoesNotReemitStartRecord() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(dataModelsById = mapOf(1u to MaxNumberScanModel))
        try {
            val key = Key<MaxNumberScanModel>(ByteArray(16) { 0xFF.toByte() })

            dataStore.execute(
                MaxNumberScanModel.add(
                    key to MaxNumberScanModel.create {
                        number with UInt.MAX_VALUE
                    }
                )
            )

            val scanResponse = dataStore.execute(
                MaxNumberScanModel.scan(
                    where = Equals(MaxNumberScanModel { number::ref } with UInt.MAX_VALUE),
                    order = Orders(MaxNumberScanModel { number::ref }.ascending()),
                    startKey = key,
                    includeStart = false
                )
            )

            assertEquals(0, scanResponse.values.size)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun exclusiveStartOnMultiValueIndexUsesMatchedSortingKey() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(dataModelsById = mapOf(1u to AnyValueSetIndexModel))
        try {
            val firstKey = (dataStore.execute(
                AnyValueSetIndexModel.add(
                    AnyValueSetIndexModel.create {
                        name with "a"
                        setValues with setOf("s3")
                    },
                    AnyValueSetIndexModel.create {
                        name with "b"
                        setValues with setOf("s1")
                    },
                    AnyValueSetIndexModel.create {
                        name with "c"
                        setValues with setOf("s2")
                    },
                    AnyValueSetIndexModel.create {
                        name with "d"
                        setValues with setOf("s4", "s0")
                    }
                )
            ).statuses.last() as AddSuccess<AnyValueSetIndexModel>).key

            val scanResponse = dataStore.execute(
                AnyValueSetIndexModel.scan(
                    order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                    startKey = firstKey,
                    includeStart = false
                )
            )

            assertEquals(listOf("b", "c", "a", "d"), scanResponse.values.map { it.values { name } })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun exclusiveStartOnMultiValueIndexCanStillReturnStartRecordAtLaterValue() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(dataModelsById = mapOf(1u to AnyValueSetIndexModel))
        try {
            val startKey = (dataStore.execute(
                AnyValueSetIndexModel.add(
                    AnyValueSetIndexModel.create {
                        name with "a"
                        setValues with setOf("s1", "s4")
                    },
                    AnyValueSetIndexModel.create {
                        name with "b"
                        setValues with setOf("s2")
                    },
                    AnyValueSetIndexModel.create {
                        name with "c"
                        setValues with setOf("s3")
                    }
                )
            ).statuses.first() as AddSuccess<AnyValueSetIndexModel>).key

            val scanResponse = dataStore.execute(
                AnyValueSetIndexModel.scan(
                    order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                    startKey = startKey,
                    includeStart = false
                )
            )

            assertEquals(listOf("b", "c", "a"), scanResponse.values.map { it.values { name } })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun descendingExclusiveStartOnMinIndexValueDoesNotThrowOrReemitStartRecord() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(dataModelsById = mapOf(1u to MaxNumberScanModel))
        try {
            val key = Key<MaxNumberScanModel>(ByteArray(16))

            dataStore.execute(
                MaxNumberScanModel.add(
                    key to MaxNumberScanModel.create {
                        number with 0u
                    }
                )
            )

            val scanResponse = dataStore.execute(
                MaxNumberScanModel.scan(
                    order = Orders(MaxNumberScanModel { number::ref }.descending()),
                    startKey = key,
                    includeStart = false
                )
            )

            assertEquals(0, scanResponse.values.size)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun filteredOrderedScanKeepsBoundaryWhenStartKeyDoesNotMatchFilter() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(dataModelsById = mapOf(1u to AnyValueSetIndexModel))
        try {
            val statuses = dataStore.execute(
                AnyValueSetIndexModel.add(
                    AnyValueSetIndexModel.create {
                        name with "a"
                        setValues with setOf("s3")
                    },
                    AnyValueSetIndexModel.create {
                        name with "b"
                        setValues with setOf("s1")
                    },
                    AnyValueSetIndexModel.create {
                        name with "c"
                        setValues with setOf("s2")
                    }
                )
            ).statuses

            val startKey = (statuses.first() as AddSuccess<AnyValueSetIndexModel>).key

            val scanResponse = dataStore.execute(
                AnyValueSetIndexModel.scan(
                    where = Equals(AnyValueSetIndexModel { setValues.refToAny() } with "s2"),
                    order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                    startKey = startKey,
                    includeStart = false
                )
            )

            assertEquals(1, scanResponse.values.size)
            assertEquals("c", scanResponse.values.single().values { name })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun orderedScanFlowCanAddValueAfterEmptyWindowWithStartKey() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(dataModelsById = mapOf(1u to AnyValueSetIndexModel))
        val responses = Channel<IsUpdateResponse<AnyValueSetIndexModel>>(2)
        var listenJob: kotlinx.coroutines.Job? = null
        try {
            suspend fun receiveRealTimeResponse() = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5000.milliseconds) { responses.receive() }
            }

            val statuses = dataStore.execute(
                AnyValueSetIndexModel.add(
                    AnyValueSetIndexModel.create {
                        name with "a"
                        setValues with setOf("s3")
                    },
                    AnyValueSetIndexModel.create {
                        name with "b"
                        setValues with setOf("s1")
                    },
                    AnyValueSetIndexModel.create {
                        name with "c"
                        setValues with setOf("s2")
                    }
                )
            ).statuses

            val startKey = (statuses.first() as AddSuccess<AnyValueSetIndexModel>).key

            listenJob = launch {
                dataStore.executeFlow(
                    AnyValueSetIndexModel.scan(
                        order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                        startKey = startKey,
                        includeStart = false
                    )
                ).collect {
                    responses.send(it)
                }
            }

            assertIs<InitialValuesUpdate<AnyValueSetIndexModel>>(receiveRealTimeResponse()).apply {
                assertEquals(emptyList(), values.map { it.key })
            }

            val addStatus = dataStore.execute(
                AnyValueSetIndexModel.add(
                    AnyValueSetIndexModel.create {
                        name with "d"
                        setValues with setOf("s4")
                    }
                )
            ).statuses.single() as AddSuccess<AnyValueSetIndexModel>

            assertIs<AdditionUpdate<AnyValueSetIndexModel>>(receiveRealTimeResponse()).apply {
                assertEquals(addStatus.key, key)
                assertEquals("d", values { name })
                assertEquals(0, insertionIndex)
            }
        } finally {
            dataStore.closeAllListeners()
            listenJob?.cancelAndJoin()
            dataStore.close()
        }
    }

    @Test
    fun orderedScanUpdatesUsesMatchedAnyValueSortingKeyRegression() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = mapOf(1u to AnyValueSetIndexModel))
        try {
            dataStore.execute(
                AnyValueSetIndexModel.add(
                    AnyValueSetIndexModel.create {
                        name with "a"
                        setValues with setOf("s3")
                    },
                    AnyValueSetIndexModel.create {
                        name with "b"
                        setValues with setOf("s1")
                    },
                    AnyValueSetIndexModel.create {
                        name with "c"
                        setValues with setOf("s2")
                    }
                )
            ).statuses.forEach {
                assertIs<AddSuccess<AnyValueSetIndexModel>>(it)
            }

            val addedObject = AnyValueSetIndexModel.create {
                name with "e"
                setValues with setOf("s4", "s0")
            }
            val addedKey = (dataStore.execute(
                AnyValueSetIndexModel.add(addedObject)
            ).statuses.single() as AddSuccess<AnyValueSetIndexModel>).key

            val getResponse = dataStore.execute(AnyValueSetIndexModel.get(addedKey))
            assertEquals(listOf(addedKey), getResponse.values.map { it.key })

            val fullScanResponse = dataStore.execute(
                AnyValueSetIndexModel.scan(
                    order = AnyValueSetIndexModel { setValues.refToAny() }.ascending()
                )
            )
            assertEquals(listOf("e", "b", "c", "a"), fullScanResponse.values.map { it.values { name } }.distinct())

            val valuesResponse = dataStore.execute(
                AnyValueSetIndexModel.scan(
                    order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                    limit = 1u
                )
            )
            assertEquals(listOf(addedKey), valuesResponse.values.map { it.key })

            val updatesResponse = dataStore.execute(
                AnyValueSetIndexModel.scanUpdates(
                    order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                    limit = 1u
                )
            )

            val orderedKeys = updatesResponse.updates.first() as OrderedKeysUpdate<AnyValueSetIndexModel>
            assertEquals(listOf(addedKey), orderedKeys.keys)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun changeUpdatesIncMapRefToAnyIndexRequestRegression() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = mapOf(1u to AnyValueIncMapIndexModel))
        try {
            val keys = dataStore.execute(
                AnyValueIncMapIndexModel.add(
                    AnyValueIncMapIndexModel.create {
                        name with "a"
                        incMapValues with mapOf(2u to "i2")
                    },
                    AnyValueIncMapIndexModel.create {
                        name with "b"
                        incMapValues with mapOf(3u to "i3")
                    },
                    AnyValueIncMapIndexModel.create {
                        name with "c"
                        incMapValues with mapOf(1u to "i1")
                    }
                )
            ).statuses.map { (it as AddSuccess<AnyValueIncMapIndexModel>).key }

            dataStore.execute(
                AnyValueIncMapIndexModel.change(
                    keys[0].change(
                        Change(
                            AnyValueIncMapIndexModel { incMapValues refAt 4u } with "i2"
                        )
                    )
                )
            ).statuses.forEach {
                assertIs<ChangeSuccess<AnyValueIncMapIndexModel>>(it)
            }

            val getResponse = dataStore.execute(AnyValueIncMapIndexModel.get(keys[0]))
            assertEquals(
                mapOf(2u to "i2", 4u to "i2"),
                getResponse.values.single().values { incMapValues }
            )

            val scanResponse = dataStore.execute(
                AnyValueIncMapIndexModel.scan(
                    where = Equals(
                        AnyValueIncMapIndexModel { incMapValues.refToAnyKey() } with 4u
                    )
                )
            )

            assertEquals(listOf("a"), scanResponse.values.map { it.values { name } }.distinct())
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun getUpdatesAtOldVersionStillFindsHardDeletedRecord() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            val addResponse = dataStore.execute(
                SimpleMarykModel.add(
                    SimpleMarykModel.create { value with "ha historic hard delete" }
                )
            )
            val addStatus = addResponse.statuses.single() as AddSuccess<SimpleMarykModel>

            val deleteResponse = dataStore.execute(
                SimpleMarykModel.delete(addStatus.key, hardDelete = true)
            )
            deleteResponse.statuses.single() as DeleteSuccess<SimpleMarykModel>

            val updatesResponse = dataStore.execute(
                SimpleMarykModel.getUpdates(addStatus.key, toVersion = addStatus.version)
            )

            val orderedKeys = updatesResponse.updates.first() as OrderedKeysUpdate<SimpleMarykModel>
            assertEquals(listOf(addStatus.key), orderedKeys.keys)

            val addition = updatesResponse.updates[1] as AdditionUpdate<SimpleMarykModel>
            assertEquals(addStatus.key, addition.key)
            assertEquals("ha historic hard delete", addition.values { value })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun exactKeyScanAtOldVersionStillFindsHardDeletedRecord() = runTest(timeout = 1.minutes) {
        val timestamp = LocalDateTime(2024, 1, 2, 3, 4, 5)
        val log = Log(
            message = "historic exact scan",
            severity = INFO,
            timestamp = timestamp
        )
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to Log)
        )
        try {
            val addResponse = dataStore.execute(Log.add(log))
            val addStatus = addResponse.statuses.single() as AddSuccess<Log>

            val deleteResponse = dataStore.execute(
                Log.delete(addStatus.key, hardDelete = true)
            )
            deleteResponse.statuses.single() as DeleteSuccess<Log>

            val scanResponse = dataStore.execute(
                Log.scan(
                    where = Equals(
                        Log.timestamp.ref() with timestamp,
                        Log.severity.ref() with INFO
                    ),
                    toVersion = addStatus.version
                )
            )

            assertEquals(1, scanResponse.values.size)
            assertEquals(addStatus.key, scanResponse.values.single().key)
            assertEquals(log, scanResponse.values.single().values)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdateHistoryCanFilterHardDeletedRecordInMemory() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            val addResponse = dataStore.execute(
                SimpleMarykModel.add(
                    SimpleMarykModel.create { value with "ha historic filtered hard delete" }
                )
            )
            val addStatus = addResponse.statuses.single() as AddSuccess<SimpleMarykModel>

            val deleteResponse = dataStore.execute(
                SimpleMarykModel.delete(addStatus.key, hardDelete = true)
            )
            val deleteStatus = deleteResponse.statuses.single() as DeleteSuccess<SimpleMarykModel>

            val updatesResponse = dataStore.execute(
                SimpleMarykModel.scanUpdateHistory(
                    fromVersion = deleteStatus.version,
                    toVersion = deleteStatus.version,
                    limit = 1u,
                    where = Equals(SimpleMarykModel { value::ref } with "ha historic filtered hard delete")
                )
            )

            val update = updatesResponse.updates.single() as RemovalUpdate<SimpleMarykModel>
            assertEquals(addStatus.key, update.key)
            assertEquals(deleteStatus.version, update.version)
            assertEquals(HardDelete, update.reason)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesCanFilterHardDeletedRecordInMemory() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            val addResponse = dataStore.execute(
                SimpleMarykModel.add(
                    SimpleMarykModel.create { value with "ha historic filtered hard delete updates" }
                )
            )
            val addStatus = addResponse.statuses.single() as AddSuccess<SimpleMarykModel>

            val deleteResponse = dataStore.execute(
                SimpleMarykModel.delete(addStatus.key, hardDelete = true)
            )
            val deleteStatus = deleteResponse.statuses.single() as DeleteSuccess<SimpleMarykModel>

            val updatesResponse = dataStore.execute(
                SimpleMarykModel.scanUpdates(
                    where = Equals(SimpleMarykModel { value::ref } with "ha historic filtered hard delete updates"),
                    limit = 1u
                )
            )

            val orderedKeys = updatesResponse.updates.first() as OrderedKeysUpdate<SimpleMarykModel>
            assertEquals(emptyList(), orderedKeys.keys)
            assertEquals(deleteStatus.version, orderedKeys.version)

            val removal = updatesResponse.updates[1] as RemovalUpdate<SimpleMarykModel>
            assertEquals(addStatus.key, removal.key)
            assertEquals(deleteStatus.version, removal.version)
            assertEquals(HardDelete, removal.reason)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun tableScanAtOldVersionStillFindsHardDeletedRecord() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            val addResponse = dataStore.execute(
                SimpleMarykModel.add(
                    SimpleMarykModel.create { value with "ha historic table scan hard delete" }
                )
            )
            val addStatus = addResponse.statuses.single() as AddSuccess<SimpleMarykModel>

            val deleteResponse = dataStore.execute(
                SimpleMarykModel.delete(addStatus.key, hardDelete = true)
            )
            deleteResponse.statuses.single() as DeleteSuccess<SimpleMarykModel>

            val scanResponse = dataStore.execute(
                SimpleMarykModel.scan(toVersion = addStatus.version)
            )

            assertEquals(1, scanResponse.values.size)
            assertEquals(addStatus.key, scanResponse.values.single().key)
            assertEquals("ha historic table scan hard delete", scanResponse.values.single().values { value })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun indexedScanAtOldVersionSkipsHardDeletedRecord() = runTest(timeout = 1.minutes) {
        val log = Log(
            message = "historic indexed scan",
            severity = INFO,
            timestamp = LocalDateTime(2024, 1, 2, 3, 4, 5)
        )
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to Log)
        )
        try {
            val addResponse = dataStore.execute(Log.add(log))
            val addStatus = addResponse.statuses.single() as AddSuccess<Log>

            val deleteResponse = dataStore.execute(
                Log.delete(addStatus.key, hardDelete = true)
            )
            deleteResponse.statuses.single() as DeleteSuccess<Log>

            val scanResponse = dataStore.execute(
                Log.scan(
                    where = Equals(Log.severity.ref() with INFO),
                    toVersion = addStatus.version,
                    order = Orders(Log { severity::ref }.ascending())
                )
            )

            assertEquals(0, scanResponse.values.size)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun uniqueScanAtOldVersionSkipsHardDeletedRecord() = runTest(timeout = 1.minutes) {
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to UniqueModel)
        )
        try {
            val addResponse = dataStore.execute(
                UniqueModel.add(
                    UniqueModel.create { email with "historic-unique@test.com" }
                )
            )
            val addStatus = addResponse.statuses.single() as AddSuccess<UniqueModel>

            val deleteResponse = dataStore.execute(
                UniqueModel.delete(addStatus.key, hardDelete = true)
            )
            deleteResponse.statuses.single() as DeleteSuccess<UniqueModel>

            val scanResponse = dataStore.execute(
                UniqueModel.scan(
                    where = Equals(UniqueModel { email::ref } with "historic-unique@test.com"),
                    toVersion = addStatus.version
                )
            )

            assertEquals(0, scanResponse.values.size)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun historicalGetPrefersArchivedRecordWhenKeyIsReused() = runTest(timeout = 1.minutes) {
        val key = SimpleMarykModel.key(validUuidV4Bytes(42))
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            val firstAddResponse = dataStore.execute(
                SimpleMarykModel.add(
                    key to SimpleMarykModel.create { value with "ha first historic value" }
                )
            )
            val firstAddStatus = firstAddResponse.statuses.single() as AddSuccess<SimpleMarykModel>

            val deleteResponse = dataStore.execute(
                SimpleMarykModel.delete(key, hardDelete = true)
            )
            deleteResponse.statuses.single() as DeleteSuccess<SimpleMarykModel>

            val secondAddResponse = dataStore.execute(
                SimpleMarykModel.add(
                    key to SimpleMarykModel.create { value with "ha second current value" }
                )
            )
            secondAddResponse.statuses.single() as AddSuccess<SimpleMarykModel>

            val getResponse = dataStore.execute(
                SimpleMarykModel.get(key, toVersion = firstAddStatus.version)
            )

            assertEquals(1, getResponse.values.size)
            assertEquals(key, getResponse.values.single().key)
            assertEquals("ha first historic value", getResponse.values.single().values { value })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun getChangesIncludesArchivedLifecycleWhenKeyIsReused() = runTest(timeout = 1.minutes) {
        val key = SimpleMarykModel.key(validUuidV4Bytes(43))
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            dataStore.execute(
                SimpleMarykModel.add(
                    key to SimpleMarykModel.create { value with "ha first history value" }
                )
            )
            dataStore.execute(SimpleMarykModel.delete(key, hardDelete = true))
            dataStore.execute(
                SimpleMarykModel.add(
                    key to SimpleMarykModel.create { value with "ha second history value" }
                )
            )

            val getChangesResponse = dataStore.execute(SimpleMarykModel.getChanges(key))

            assertEquals(1, getChangesResponse.changes.size)
            val changes = getChangesResponse.changes.single().changes
            assertEquals(2, changes.size)
            assertEquals(listOf(ObjectCreate), changes[0].changes.take(1))
            assertEquals(listOf(ObjectCreate), changes[1].changes.take(1))
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun getUpdatesIncludesArchivedLifecycleWhenKeyIsReused() = runTest(timeout = 1.minutes) {
        val key = SimpleMarykModel.key(validUuidV4Bytes(44))
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            dataStore.execute(
                SimpleMarykModel.add(
                    key to SimpleMarykModel.create { value with "ha first updates value" }
                )
            )
            dataStore.execute(SimpleMarykModel.delete(key, hardDelete = true))
            dataStore.execute(
                SimpleMarykModel.add(
                    key to SimpleMarykModel.create { value with "ha second updates value" }
                )
            )

            val updatesResponse = dataStore.execute(SimpleMarykModel.getUpdates(key))

            val orderedKeys = updatesResponse.updates.first() as OrderedKeysUpdate<SimpleMarykModel>
            assertEquals(listOf(key), orderedKeys.keys)

            val firstAddition = updatesResponse.updates[1] as AdditionUpdate<SimpleMarykModel>
            val secondAddition = updatesResponse.updates[2] as AdditionUpdate<SimpleMarykModel>
            assertEquals("ha first updates value", firstAddition.values { value })
            assertEquals("ha second updates value", secondAddition.values { value })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdatesIncludesArchivedLifecycleWhenKeyIsReused() = runTest(timeout = 1.minutes) {
        val key = SimpleMarykModel.key(validUuidV4Bytes(45))
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            dataStore.execute(
                SimpleMarykModel.add(
                    key to SimpleMarykModel.create { value with "ha first scan updates value" }
                )
            )
            dataStore.execute(SimpleMarykModel.delete(key, hardDelete = true))
            dataStore.execute(
                SimpleMarykModel.add(
                    key to SimpleMarykModel.create { value with "ha second scan updates value" }
                )
            )

            val updatesResponse = dataStore.execute(SimpleMarykModel.scanUpdates())

            val orderedKeys = updatesResponse.updates.first() as OrderedKeysUpdate<SimpleMarykModel>
            assertEquals(listOf(key), orderedKeys.keys)

            val firstAddition = updatesResponse.updates[1] as AdditionUpdate<SimpleMarykModel>
            val secondAddition = updatesResponse.updates[2] as AdditionUpdate<SimpleMarykModel>
            assertEquals("ha first scan updates value", firstAddition.values { value })
            assertEquals("ha second scan updates value", secondAddition.values { value })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun reusedKeyGetChangesAppliesMaxVersionsGlobally() = runTest(timeout = 1.minutes) {
        val key = SimpleMarykModel.key(validUuidV4Bytes(46))
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            val firstAddVersion = (dataStore.execute(
                SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha first get changes value" })
            ).statuses.single() as AddSuccess<SimpleMarykModel>).version

            val firstChange = Change(SimpleMarykModel { value::ref } with "ha first get changes update")
            dataStore.execute(
                SimpleMarykModel.change(key.change(firstChange))
            ).statuses.single() as ChangeSuccess<SimpleMarykModel>

            dataStore.execute(SimpleMarykModel.delete(key, hardDelete = true))

            val secondAddVersion = (dataStore.execute(
                SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha second get changes value" })
            ).statuses.single() as AddSuccess<SimpleMarykModel>).version

            val secondChange = Change(SimpleMarykModel { value::ref } with "ha second get changes update")
            val secondChangeVersion = (dataStore.execute(
                SimpleMarykModel.change(key.change(secondChange))
            ).statuses.single() as ChangeSuccess<SimpleMarykModel>).version

            val getChangesResponse = dataStore.execute(
                SimpleMarykModel.getChanges(key, maxVersions = 1u)
            )

            assertEquals(1, getChangesResponse.changes.size)
            val changes = getChangesResponse.changes.single().changes
            assertEquals(listOf(firstAddVersion, secondAddVersion, secondChangeVersion), changes.map { it.version })
            assertEquals(listOf(ObjectCreate), changes[0].changes.take(1))
            assertEquals(listOf(ObjectCreate), changes[1].changes.take(1))
            assertEquals(listOf(secondChange), changes[2].changes)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun reusedKeyScanChangesAppliesMaxVersionsGlobally() = runTest(timeout = 1.minutes) {
        val key = SimpleMarykModel.key(validUuidV4Bytes(47))
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            val firstAddVersion = (dataStore.execute(
                SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha first scan changes value" })
            ).statuses.single() as AddSuccess<SimpleMarykModel>).version

            val firstChange = Change(SimpleMarykModel { value::ref } with "ha first scan changes update")
            dataStore.execute(SimpleMarykModel.change(key.change(firstChange))).statuses.single() as ChangeSuccess<SimpleMarykModel>

            dataStore.execute(SimpleMarykModel.delete(key, hardDelete = true))

            val secondAddVersion = (dataStore.execute(
                SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha second scan changes value" })
            ).statuses.single() as AddSuccess<SimpleMarykModel>).version

            val secondChange = Change(SimpleMarykModel { value::ref } with "ha second scan changes update")
            val secondChangeVersion = (dataStore.execute(
                SimpleMarykModel.change(key.change(secondChange))
            ).statuses.single() as ChangeSuccess<SimpleMarykModel>).version

            val scanChangesResponse = dataStore.execute(
                SimpleMarykModel.scanChanges(maxVersions = 1u)
            )

            assertEquals(1, scanChangesResponse.changes.size)
            val changes = scanChangesResponse.changes.single().changes
            assertEquals(listOf(firstAddVersion, secondAddVersion, secondChangeVersion), changes.map { it.version })
            assertEquals(listOf(ObjectCreate), changes[0].changes.take(1))
            assertEquals(listOf(ObjectCreate), changes[1].changes.take(1))
            assertEquals(listOf(secondChange), changes[2].changes)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun reusedKeyGetUpdatesAppliesMaxVersionsGlobally() = runTest(timeout = 1.minutes) {
        val key = SimpleMarykModel.key(validUuidV4Bytes(48))
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            dataStore.execute(
                SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha first get updates value" })
            ).statuses.single() as AddSuccess<SimpleMarykModel>

            val firstChange = Change(SimpleMarykModel { value::ref } with "ha first get updates update")
            dataStore.execute(SimpleMarykModel.change(key.change(firstChange))).statuses.single() as ChangeSuccess<SimpleMarykModel>

            dataStore.execute(SimpleMarykModel.delete(key, hardDelete = true))

            dataStore.execute(
                SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha second get updates value" })
            ).statuses.single() as AddSuccess<SimpleMarykModel>

            val secondChange = Change(SimpleMarykModel { value::ref } with "ha second get updates update")
            val secondChangeVersion = (dataStore.execute(
                SimpleMarykModel.change(key.change(secondChange))
            ).statuses.single() as ChangeSuccess<SimpleMarykModel>).version

            val updatesResponse = dataStore.execute(
                SimpleMarykModel.getUpdates(key, maxVersions = 1u)
            )

            assertEquals(4, updatesResponse.updates.size)
            val firstAddition = updatesResponse.updates[1] as AdditionUpdate<SimpleMarykModel>
            val secondAddition = updatesResponse.updates[2] as AdditionUpdate<SimpleMarykModel>
            val latestChange = updatesResponse.updates[3] as ChangeUpdate<SimpleMarykModel>

            assertEquals("ha first get updates value", firstAddition.values { value })
            assertEquals("ha second get updates value", secondAddition.values { value })
            assertEquals(secondChangeVersion, latestChange.version)
            assertEquals(listOf(secondChange), latestChange.changes)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun reusedKeyScanUpdatesAppliesMaxVersionsGlobally() = runTest(timeout = 1.minutes) {
        val key = SimpleMarykModel.key(validUuidV4Bytes(49))
        val dataStore = InMemoryDataStore.open(
            keepAllVersions = true,
            dataModelsById = mapOf(1u to SimpleMarykModel)
        )
        try {
            dataStore.execute(
                SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha first scan updates limited value" })
            ).statuses.single() as AddSuccess<SimpleMarykModel>

            val firstChange = Change(SimpleMarykModel { value::ref } with "ha first scan updates limited update")
            dataStore.execute(SimpleMarykModel.change(key.change(firstChange))).statuses.single() as ChangeSuccess<SimpleMarykModel>

            dataStore.execute(SimpleMarykModel.delete(key, hardDelete = true))

            dataStore.execute(
                SimpleMarykModel.add(key to SimpleMarykModel.create { value with "ha second scan updates limited value" })
            ).statuses.single() as AddSuccess<SimpleMarykModel>

            val secondChange = Change(SimpleMarykModel { value::ref } with "ha second scan updates limited update")
            val secondChangeVersion = (dataStore.execute(
                SimpleMarykModel.change(key.change(secondChange))
            ).statuses.single() as ChangeSuccess<SimpleMarykModel>).version

            val updatesResponse = dataStore.execute(
                SimpleMarykModel.scanUpdates(maxVersions = 1u)
            )

            assertEquals(4, updatesResponse.updates.size)
            val firstAddition = updatesResponse.updates[1] as AdditionUpdate<SimpleMarykModel>
            val secondAddition = updatesResponse.updates[2] as AdditionUpdate<SimpleMarykModel>
            val latestChange = updatesResponse.updates[3] as ChangeUpdate<SimpleMarykModel>

            assertEquals("ha first scan updates limited value", firstAddition.values { value })
            assertEquals("ha second scan updates limited value", secondAddition.values { value })
            assertEquals(secondChangeVersion, latestChange.version)
            assertEquals(listOf(secondChange), latestChange.changes)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun closeAllListenersBeforeRequestsDoesNotHang() = runTest {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        try {
            withContext(Dispatchers.Default) {
                withTimeout(1.seconds) {
                    dataStore.closeAllListeners()
                }
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun closeAllListenersAfterCloseDoesNotHang() = runTest {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        dataStore.close()

        withContext(Dispatchers.Default) {
            withTimeout(1.seconds) {
                dataStore.closeAllListeners()
            }
        }
    }

    @Test
    fun executeAfterCloseFailsImmediately() = runTest {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        dataStore.close()

        assertFailsWith<StorageException> {
            dataStore.execute(SimpleMarykModel.get(SimpleMarykModel.key(ByteArray(16))))
        }
    }

    @Test
    fun cancellingFlowAfterCloseDoesNotFailRemovingListener() = runTest {
        val dataStore = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        val key = SimpleMarykModel.key(ByteArray(16))
        dataStore.execute(SimpleMarykModel.add(key to SimpleMarykModel.create { value with "flow close" }))

        val flow = dataStore.executeFlow(SimpleMarykModel.getUpdates(key))
        val collector = launch {
            flow.collect()
        }

        try {
            dataStore.close()
            collector.cancelAndJoin()
        } finally {
            dataStore.close()
        }
    }
}

private object MaxNumberScanModel : RootDataModel<MaxNumberScanModel>(
    keyDefinition = { UUIDv4Key },
    indexes = { listOf(MaxNumberScanModel.number.ref()) }
) {
    val number by number(
        index = 1u,
        type = UInt32
    )
}

private fun validUuidV4Bytes(value: Int): ByteArray =
    ByteArray(16) { value.toByte() }.apply {
        this[6] = ((this[6].toInt() and 0x0F) or 0x40).toByte()
        this[8] = ((this[8].toInt() and 0x3F) or 0x80).toByte()
    }
