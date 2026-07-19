package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.MigrationOutcome
import maryk.core.models.migration.MigrationRetryPolicy
import maryk.core.properties.definitions.fixedBytes
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import maryk.datastore.test.DataStoreAddTest
import maryk.datastore.test.DataStoreChangeComplexTest
import maryk.datastore.test.DataStoreChangeTest
import maryk.datastore.test.DataStoreChangeValidationTest
import maryk.datastore.test.DataStoreDeleteTest
import maryk.datastore.test.DataStoreFilterComplexTest
import maryk.datastore.test.DataStoreFilterTest
import maryk.datastore.test.DataStoreGetChangesTest
import maryk.datastore.test.DataStoreGetTest
import maryk.datastore.test.DataStoreGetUpdatesAndFlowTest
import maryk.datastore.test.DataStoreGeoTest
import maryk.datastore.test.DataStoreProcessUpdateTest
import maryk.datastore.test.DataStoreScanChangesTest
import maryk.datastore.test.DataStoreScanMultiTypeTest
import maryk.datastore.test.DataStoreScanOnAnyValueIndexTest
import maryk.datastore.test.DataStoreScanOnIndexTest
import maryk.datastore.test.DataStoreScanOnIndexWithPersonTest
import maryk.datastore.test.DataStoreScanOnNormalizeIndexTest
import maryk.datastore.test.DataStoreScanTest
import maryk.datastore.test.DataStoreScanUpdateHistoryTest
import maryk.datastore.test.DataStoreScanUniqueTest
import maryk.datastore.test.DataStoreScanUpdatesAndFlowTest
import maryk.datastore.test.DataStoreScanWithFilterTest
import maryk.datastore.test.DataStoreScanWithMutableValueIndexTest
import maryk.datastore.test.IsDataStoreTest
import maryk.datastore.test.UniqueTest
import maryk.datastore.test.assertStatusIs
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.ModelV1
import maryk.test.models.ModelV1_1
import maryk.test.models.ModelV2
import maryk.test.models.ModelV2ExtraIndex
import maryk.test.models.Person
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

class IndexedDbDataStoreTest {
    @Test
    fun reusesSharedAddGetScanTests() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
        )

        try {
            runTestCase(DataStoreAddTest(dataStore), "executeAddAndSimpleGetRequest")
            runTestCase(DataStoreAddTest(dataStore), "executeAddWithKeyAndSimpleGetRequest")
            runTestCase(DataStoreAddTest(dataStore), "notAddSameObjectTwice")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeCheckRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeChangeRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeDeleteRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeDeleteComplexRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeDeleteComplexItemsRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeListRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeSetRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeMapRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeNoOpListAndMapRequest")
            runTestCase(DataStoreChangeTest(dataStore), "executeChangeIncMapRequest")
            runTestCase(DataStoreChangeComplexTest(dataStore), "executeChangeDeleteMapSubValueRequest")
            runTestCase(DataStoreChangeComplexTest(dataStore), "executeChangeDeleteMapTypedSubValueRequest")
            runTestCase(DataStoreChangeComplexTest(dataStore), "executeChangeChangeValueRequest")
            runTestCase(DataStoreChangeComplexTest(dataStore), "executeChangeInsertValueRequest")
            runTestCase(DataStoreChangeValidationTest(dataStore), "executeChangeChangeWithValidationExceptionRequest")
            runTestCase(DataStoreChangeValidationTest(dataStore), "executeChangeListWithTooManyItemsValidationExceptionRequest")
            runTestCase(DataStoreChangeValidationTest(dataStore), "executeChangeSetWithMaxSizeValidationExceptionRequest")
            runTestCase(DataStoreChangeValidationTest(dataStore), "executeChangeMapWithSizeValidationExceptionRequest")
            runTestCase(DataStoreChangeValidationTest(dataStore), "executeChangeListSizeValidationExceptionRequest")
            runTestCase(DataStoreDeleteTest(dataStore), "processHardDeleteRequest")
            runTestCase(DataStoreGetChangesTest(dataStore), "executeSimpleGetChangesRequest")
            runTestCase(DataStoreGetChangesTest(dataStore), "executeToVersionGetChangesRequest")
            runTestCase(DataStoreGetChangesTest(dataStore), "executeFromVersionGetChangesRequest")
            runTestCase(DataStoreGetChangesTest(dataStore), "executeGetChangesRequestWithSelect")
            runTestCase(DataStoreGetChangesTest(dataStore), "executeGetChangesRequestWithMaxVersions")
            runTestCase(DataStoreGetTest(dataStore), "executeSimpleGetRequest")
            runTestCase(DataStoreGetUpdatesAndFlowTest(dataStore), "executeSimpleGetUpdatesRequest")
            runTestCase(DataStoreFilterTest(dataStore), "doComplexMapListSetFilter")
            runTestCase(DataStoreFilterTest(dataStore), "doReferencedEqualsFilter")
            runTestCase(DataStoreFilterComplexTest(dataStore), "doEqualsFilter")
            DataStoreGeoTest(dataStore).allTests.keys.forEach { name ->
                runTestCase(DataStoreGeoTest(dataStore), name)
            }
            runTestCase(DataStoreScanChangesTest(dataStore), "executeSimpleScanChangesRequest")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeSimpleScanReversedChangesRequest")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesOnAscendingIndexRequest")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesOnDescendingIndexRequest")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesRequestWithLimit")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesRequestWithToVersion")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesRequestWithFromVersion")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesRequestWithSelect")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesRequestWithMaxVersions")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessAddRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessChangeRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessAddInChangeRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessRemovalRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "executeProcessInitialChangesRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "failOnInitialValuesRequest")
            runTestCase(DataStoreProcessUpdateTest(dataStore), "failOnOrderedKeysUpdateRequest")
            runTestCase(DataStoreScanUpdateHistoryTest(dataStore), "executeScanUpdateHistoryFailsWithoutIndex")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeSimpleScanUpdatesRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeOrderedScanUpdatesRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "uncollectedFlowDoesNotBlockWritesOrLaterListeners")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowWithMutableWhereRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesIncludingInitValuesAsFlowRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowWithSelectRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeReversedScanUpdatesAsFlowRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeOrderedScanUpdatesAsFlowRequest")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeReverseOrderedScanUpdatesAsFlowRequest")
            runTestCase(DataStoreGetTest(dataStore), "executeSimpleGetWithAggregationRequest")
            runTestCase(DataStoreGetTest(dataStore), "executeGetRequestWithSelect")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanRequest")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanWithAggregationRequest")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanRequestReverseOrder")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanReverseOrderFromAbsentStartKey")
            runTestCase(DataStoreScanTest(dataStore), "executeScanRequestWithLimit")
            runTestCase(DataStoreScanTest(dataStore), "executeScanRequestWithSelect")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanFilterRequest")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanFilterExactMatchRequest")
            runTestCase(DataStoreScanTest(dataStore), "executeSimpleScanFilterExactWrongMatchRequest")
            runTestCase(DataStoreScanWithFilterTest(dataStore), "executeSimpleScanFilterRequest")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeSimpleIndexScanRequest")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeSimpleIndexScanWithStartKeyRequest")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeSimpleIndexScanRequestReverseOrder")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeIndexScanRequestWithLimit")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeIndexScanWithMultiRangeLimit")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeIndexScanRequestWithSelect")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeSimpleIndexFilterScanRequest")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeSimpleIndexFilterGreaterScanRequest")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeSimpleIndexFilterLessScanRequest")
            runTestCase(DataStoreScanUniqueTest(dataStore), "executeSimpleScanFilterRequest")
            runTestCase(UniqueTest(dataStore), "checkUnique")
            runTestCase(UniqueTest(dataStore), "checkUniqueAddDuplicate")
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun reusesSharedUpdateHistoryTests() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-history-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        try {
            runTestCase(DataStoreGetTest(dataStore), "executeToVersionGetRequest")
            runTestCase(DataStoreScanTest(dataStore), "executeScanRequestWithToVersion")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeIndexScanRequestWithToVersionAscending")
            runTestCase(DataStoreScanOnIndexTest(dataStore), "executeIndexScanRequestWithToVersionDescending")
            runTestCase(DataStoreScanUniqueTest(dataStore), "executeSimpleScanFilterWithToVersionRequest")
            runTestCase(DataStoreScanUniqueTest(dataStore), "executeHistoricalUniqueDoesNotMatchPrefixCollision")
            runTestCase(DataStoreScanUniqueTest(dataStore), "executeHistoricalUniqueCanFindHardDeletedObject")
            runTestCase(DataStoreScanUniqueTest(dataStore), "executeHistoricalUniqueCanIncludeSoftDeletedObject")
            runTestCase(DataStoreScanUniqueTest(dataStore), "executeHistoricalUniqueCanIncludeObjectSoftDeletedByChange")
            runTestCase(DataStoreScanChangesTest(dataStore), "executeScanChangesOnIndexRequestWithToVersion")
            runTestCase(DataStoreScanUpdateHistoryTest(dataStore), "executeScanUpdateHistoryReturnsVersionOrderedEntries")
            runTestCase(DataStoreScanUpdateHistoryTest(dataStore), "executeScanUpdateHistoryCanIncludeSoftDeleteAtHistoricVersion")
            runTestCase(DataStoreScanUpdateHistoryTest(dataStore), "executeScanUpdateHistoryCanIncludeHardDelete")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeSimpleScanUpdatesRequestWithUpdateHistoryIndex")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndex")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexTracksNewTopKey")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexStartKey")
            runTestCase(DataStoreScanUpdatesAndFlowTest(dataStore), "executeScanUpdatesAsFlowRequestWithUpdateHistoryIndexRefillsAfterDeletion")
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun reusesSharedAdvancedIndexTests() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-advanced-index-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            DataStoreScanOnIndexWithPersonTest(dataStore).allTests.forEach { (name, _) ->
                runTestCase(DataStoreScanOnIndexWithPersonTest(dataStore), name)
            }
            DataStoreScanOnAnyValueIndexTest(dataStore).allTests.forEach { (name, _) ->
                runTestCase(DataStoreScanOnAnyValueIndexTest(dataStore), name)
            }
            DataStoreScanOnNormalizeIndexTest(dataStore).allTests.forEach { (name, _) ->
                runTestCase(DataStoreScanOnNormalizeIndexTest(dataStore), name)
            }
            DataStoreScanMultiTypeTest(dataStore).allTests.forEach { (name, _) ->
                runTestCase(DataStoreScanMultiTypeTest(dataStore), name)
            }
            DataStoreScanWithMutableValueIndexTest(dataStore).allTests.forEach { (name, _) ->
                runTestCase(DataStoreScanWithMutableValueIndexTest(dataStore), name)
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun filteredTableScanLimitCountsMatchedRows() = runTest {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-filter-limit-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
        )
        val keys = mutableListOf<Key<TestMarykModel>>()

        try {
            val objects = Array(6) { index ->
                TestMarykModel.create {
                    string with "haha-miss-$index"
                    int with 0
                    uint with index.toUInt()
                    double with index.toDouble()
                    dateTime with LocalDateTime(2021, 1, index + 1, 12, 0)
                    bool with false
                }
            }

            val add = dataStore.execute(TestMarykModel.add(*objects))
            add.statuses.forEach { status ->
                keys += assertStatusIs<AddSuccess<TestMarykModel>>(status).key
            }

            val matchingKeys = keys.sortedWith { first, second -> first.bytes compareToBytes second.bytes }.takeLast(2)
            matchingKeys.forEach { key ->
                val change = dataStore.execute(
                    TestMarykModel.change(
                        key.change(Change(TestMarykModel.string.ref() with "haha-match"))
                    )
                )
                assertStatusIs<ChangeSuccess<*>>(change.statuses.single())
            }

            val scan = dataStore.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel.string.ref() with "haha-match"),
                    limit = 2u,
                    allowTableScan = true,
                )
            )

            assertEquals(2, scan.values.size)
            assertEquals(matchingKeys.toSet(), scan.values.map { it.key }.toSet())
        } finally {
            if (keys.isNotEmpty()) {
                dataStore.execute(TestMarykModel.delete(*keys.toTypedArray(), hardDelete = true))
            }
            dataStore.close()
        }
    }

    @Test
    fun filteredScanUpdateHistoryLimitCountsMatchedRows() = runTest {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-history-filter-limit-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )
        val keys = mutableListOf<Key<TestMarykModel>>()

        try {
            suspend fun addObject(intValue: Int) {
                val add = dataStore.execute(
                    TestMarykModel.add(
                        TestMarykModel.create {
                            string with "haha-history-filter-$intValue-${keys.size}"
                            int with intValue
                            uint with keys.size.toUInt()
                            double with keys.size.toDouble()
                            dateTime with LocalDateTime(2021, 2, keys.size + 1, 12, 0)
                            bool with false
                        }
                    )
                )
                keys += assertStatusIs<AddSuccess<TestMarykModel>>(add.statuses.single()).key
            }

            repeat(2) { addObject(5) }
            repeat(4) { addObject(0) }

            val history = dataStore.execute(
                TestMarykModel.scanUpdateHistory(
                    where = Equals(TestMarykModel.int.ref() with 5),
                    limit = 2u,
                )
            )

            assertEquals(2, history.updates.size)
            assertEquals(keys.take(2).toSet(), history.updates.filterIsInstance<AdditionUpdate<TestMarykModel>>().map { it.key }.toSet())
        } finally {
            if (keys.isNotEmpty()) {
                dataStore.execute(TestMarykModel.delete(*keys.toTypedArray(), hardDelete = true))
            }
            dataStore.close()
        }
    }

    @Test
    fun openWritesStoreMetadata() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-metadata-${Random.nextInt()}"
        val dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )
        dataStore.close()

        val byteStore = openIndexedDbByteStore(databaseName, setOf("meta"))
        try {
            val options = byteStore.get("meta", byteArrayOf(1))?.decodeToString()
            val models = byteStore.get("meta", byteArrayOf(2))?.decodeToString()

            assertEquals(
                "schemaVersion=2\nkeepAllVersions=true\nkeepUpdateHistoryIndex=true\n",
                options,
            )
            assertEquals(true, models?.contains("TestMarykModel") == true)
        } finally {
            byteStore.close()
        }
    }

    @Test
    fun openRejectsIncompatibleStoreOptions() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-metadata-options-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(2u to SimpleMarykModel),
            keepAllVersions = false,
            keepUpdateHistoryIndex = false,
        ).close()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(2u to SimpleMarykModel),
                keepAllVersions = true,
                keepUpdateHistoryIndex = false,
            )
        }
    }

    @Test
    fun openRejectsChangedStoredModelSignature() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-metadata-model-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(2u to SimpleMarykModel),
        ).close()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(2u to CompleteMarykModel),
            )
        }
    }

    @Test
    fun openMigratesSafeAdds() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-safe-${Random.nextInt()}"
        var dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        )
        val add = dataStore.execute(
            ModelV1.add(ModelV1.create { value with "haha-safe" })
        )
        val key = assertIs<AddSuccess<ModelV1>>(add.statuses.single()).key
        dataStore.close()

        dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1_1),
        )
        try {
            val get = dataStore.execute(ModelV1_1.get(Key<ModelV1_1>(key.bytes)))

            assertEquals(1, get.values.size)
            assertEquals("haha-safe", get.values.single().values { value })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun openBackfillsNewIndexRows() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-index-${Random.nextInt()}"
        var dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
            keepAllVersions = true,
        )
        val add = dataStore.execute(
            ModelV2.add(
                ModelV2.create { value with "ha1"; newNumber with 100 },
                ModelV2.create { value with "ha2"; newNumber with 50 },
                ModelV2.create { value with "ha3"; newNumber with 3500 },
                ModelV2.create { value with "ha4"; newNumber with 1 },
            )
        )
        val keys = add.statuses.map { status -> assertIs<AddSuccess<ModelV2>>(status).key }
        val change = dataStore.execute(
            ModelV2.change(
                keys[0].change(Change(ModelV2 { newNumber::ref } with 40)),
                keys[1].change(Change(ModelV2 { newNumber::ref } with 2000)),
                keys[2].change(Change(ModelV2 { newNumber::ref } with 500)),
                keys[3].change(Change(ModelV2 { newNumber::ref } with 990)),
            )
        )
        change.statuses.forEach { status -> assertIs<ChangeSuccess<ModelV2>>(status) }
        dataStore.close()

        dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2ExtraIndex),
            keepAllVersions = true,
        )
        try {
            val currentScan = dataStore.execute(
                ModelV2ExtraIndex.scan(order = ModelV2ExtraIndex { newNumber::ref }.ascending())
            )
            val historicScan = dataStore.execute(
                ModelV2ExtraIndex.scan(
                    order = ModelV2ExtraIndex { newNumber::ref }.descending(),
                    toVersion = ULong.MAX_VALUE,
                )
            )

            assertEquals(listOf(40, 500, 990, 2000), currentScan.values.map { it.values { newNumber } })
            assertEquals(listOf(2000, 990, 500, 40), historicScan.values.map { it.values { newNumber } })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun openRunsExplicitMigrationHandlerForUnsafeChanges() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-handler-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV2),
            )
        }

        var calls = 0
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationHandler = {
                    calls++
                    MigrationOutcome.Success
                }
            ),
        ).close()

        assertEquals(1, calls)
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
        ).close()
    }

    @Test
    fun openRejectsUnboundedMigrationRetry() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-retry-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = databaseName,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { MigrationOutcome.Retry() }
                ),
            )
        }
    }

    @Test
    fun openAppliesBoundedMigrationRetryPolicy() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-bounded-retry-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        var calls = 0
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationHandler = {
                    calls++
                    if (calls == 1) MigrationOutcome.Retry() else MigrationOutcome.Success
                },
                migrationRetryPolicy = MigrationRetryPolicy(maxRetryOutcomes = 1u),
            ),
        ).close()

        assertEquals(2, calls)
    }

    @Test
    fun migrationRetryAttemptsArePhaseLocal() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-migration-phase-retry-${Random.nextInt()}"
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        val backfillAttempts = mutableListOf<UInt>()
        var expandCalls = 0
        IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationExpandHandler = {
                    expandCalls++
                    if (expandCalls == 1) MigrationOutcome.Retry() else MigrationOutcome.Success
                },
                migrationHandler = {
                    backfillAttempts += it.attempt
                    MigrationOutcome.Success
                },
                migrationRetryPolicy = MigrationRetryPolicy(maxRetryOutcomes = 1u),
            ),
        ).close()

        assertEquals(listOf(1u), backfillAttempts)
    }

    @Test
    fun sensitivePropertyStoredEncrypted() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-encryption-${Random.nextInt()}"
        val dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(901u to SensitiveRecord),
            fieldEncryptionProvider = XorFieldEncryptionProvider(),
        )

        try {
            val addResult = dataStore.execute(
                SensitiveRecord.add(
                    SensitiveRecord(Bytes(ByteArray(16) { it.toByte() }), "hello", "top-secret")
                )
            )
            val key = assertIs<AddSuccess<SensitiveRecord>>(addResult.statuses.single()).key
            val get = dataStore.execute(SensitiveRecord.get(key))
            assertEquals("top-secret", get.values.single().values[SensitiveRecord.secret.ref()])
        } finally {
            dataStore.close()
        }

        val byteStore = openIndexedDbByteStore(databaseName, setOf("t:901"))
        try {
            val sensitiveRef = SensitiveRecord.secret.ref().toStorageByteArray()
            val rawStored = byteStore.get("t:901", createTableRowKey(ByteArray(16) { it.toByte() }, sensitiveRef))
            assertNotNull(rawStored)

            val plain = SensitiveRecord.secret.definition.toStorageBytes("top-secret", TypeIndicator.NoTypeIndicator.byte)
            assertFalse(rawStored.contentEquals(plain))
        } finally {
            byteStore.close()
        }
    }

    @Test
    fun sensitivePropertyRequiresEncryptionProvider() = runTest {
        installIndexedDbForTests()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = "maryk-indexeddb-encryption-missing-${Random.nextInt()}",
                dataModelsById = mapOf(901u to SensitiveRecord),
            )
        }
    }

    @Test
    fun sensitivePropertyCannotBeIndexed() = runTest {
        installIndexedDbForTests()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = "maryk-indexeddb-encryption-indexed-${Random.nextInt()}",
                dataModelsById = mapOf(902u to SensitiveIndexedRecord),
                fieldEncryptionProvider = XorFieldEncryptionProvider(),
            )
        }
    }

    @Test
    fun sensitiveUniqueRequiresTokenProvider() = runTest {
        installIndexedDbForTests()

        assertFailsWith<RequestException> {
            IndexedDbDataStore.open(
                databaseName = "maryk-indexeddb-encryption-unique-provider-${Random.nextInt()}",
                dataModelsById = mapOf(904u to SensitiveUniqueRecord),
                fieldEncryptionProvider = XorFieldEncryptionProvider(),
            )
        }
    }

    @Test
    fun sensitiveUniqueUsesDeterministicToken() = runTest {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-encryption-unique-${Random.nextInt()}",
            dataModelsById = mapOf(904u to SensitiveUniqueRecord),
            fieldEncryptionProvider = XorWithTokenFieldEncryptionProvider(),
        )

        try {
            val firstResult: IsAddResponseStatus<SensitiveUniqueRecord> = dataStore.execute(
                SensitiveUniqueRecord.add(
                    SensitiveUniqueRecord(Bytes(ByteArray(16) { 1 }), "same-secret")
                )
            ).statuses.single()
            assertIs<AddSuccess<SensitiveUniqueRecord>>(firstResult)

            val secondResult: IsAddResponseStatus<SensitiveUniqueRecord> = dataStore.execute(
                SensitiveUniqueRecord.add(
                    SensitiveUniqueRecord(Bytes(ByteArray(16) { 2 }), "same-secret")
                )
            ).statuses.single()
            assertIs<ValidationFail<SensitiveUniqueRecord>>(secondResult)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun sensitiveUniqueDoesNotWritePreviousRotationToken() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-encryption-rotation-${Random.nextInt()}"
        val rotatingStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(904u to SensitiveUniqueRecord),
            fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(2, listOf(1)),
        )
        try {
            val result: IsAddResponseStatus<SensitiveUniqueRecord> = rotatingStore.execute(
                SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 1 }), "same-secret"))
            ).statuses.single()
            assertIs<AddSuccess<SensitiveUniqueRecord>>(result)
        } finally {
            rotatingStore.close()
        }

        val previousKeyStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(904u to SensitiveUniqueRecord),
            fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(1),
        )
        try {
            val result: IsAddResponseStatus<SensitiveUniqueRecord> = previousKeyStore.execute(
                SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 2 }), "same-secret"))
            ).statuses.single()
            assertIs<AddSuccess<SensitiveUniqueRecord>>(result)
        } finally {
            previousKeyStore.close()
        }
    }

    @Test
    fun indexedScanLimitCountsMatchedRowsAfterSoftDeleteSkips() = runTest(timeout = indexedDbLongTestTimeout) {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-index-limit-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
        )
        val keys = mutableListOf<Key<Person>>()

        try {
            val persons = Array(258) { index ->
                Person.create {
                    firstName with "A${index.toString().padStart(3, '0')}"
                    surname with "Paged"
                }
            }

            val add = dataStore.execute(Person.add(*persons))
            add.statuses.forEach { status ->
                keys += assertStatusIs<AddSuccess<Person>>(status).key
            }

            val delete = dataStore.execute(
                Person.delete(*keys.take(256).toTypedArray(), hardDelete = false)
            )
            delete.statuses.forEach { status ->
                assertStatusIs<DeleteSuccess<Person>>(status)
            }

            val scan = dataStore.execute(
                Person.scan(
                    order = Orders(Person { surname::ref }.ascending(), Person { firstName::ref }.ascending()),
                    limit = 2u,
                )
            )

            assertEquals(2, scan.values.size)
            assertEquals(listOf(keys[256], keys[257]), scan.values.map { it.key })
        } finally {
            if (keys.isNotEmpty()) {
                dataStore.execute(Person.delete(*keys.toTypedArray(), hardDelete = true))
            }
            dataStore.close()
        }
    }

    @Test
    fun hardDeletePurgesChangedHistoricUniqueRows() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-hard-delete-purge-${Random.nextInt()}"
        val dataStore = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        val values = CompleteMarykModel.create {
            string with "haas"
            number with 24u
            subModel with SimpleMarykModel.create {
                value with "haha"
            }
            multi with T2(22)
            booleanForKey with true
            dateForKey with LocalDate(2018, 3, 29)
            multiForKey with S1("hii")
            enumEmbedded with E1
        }

        try {
            val add = dataStore.execute(CompleteMarykModel.add(values))
            val addStatus = assertStatusIs<AddSuccess<*>>(add.statuses.single())
            @Suppress("UNCHECKED_CAST")
            val key = addStatus.key as Key<CompleteMarykModel>

            val change = dataStore.execute(
                CompleteMarykModel.change(
                    key.change(Change(CompleteMarykModel.string.ref() with "haas2"))
                )
            )
            val changeStatus = assertStatusIs<ChangeSuccess<*>>(change.statuses.single())

            val delete = dataStore.execute(CompleteMarykModel.delete(key, hardDelete = true))
            assertStatusIs<DeleteSuccess<*>>(delete.statuses.single())

            val oldUnique = dataStore.execute(
                CompleteMarykModel.scan(
                    where = Equals(CompleteMarykModel.string.ref() with "haas"),
                    toVersion = addStatus.version,
                    filterSoftDeleted = false,
                )
            )
            val newUnique = dataStore.execute(
                CompleteMarykModel.scan(
                    where = Equals(CompleteMarykModel.string.ref() with "haas2"),
                    toVersion = changeStatus.version,
                    filterSoftDeleted = false,
                )
            )

            assertEquals(0, oldUnique.values.size)
            assertEquals(0, newUnique.values.size)

            val byteStore = openIndexedDbByteStore(databaseName, setOf("hik:5", "huk:5"))
            try {
                assertEquals(0, byteStore.scan("hik:5").size)
                assertEquals(0, byteStore.scan("huk:5").size)
            } finally {
                byteStore.close()
            }
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun scanUpdateHistoryReconstructsUnserializableCreationLogs() = runTest {
        installIndexedDbForTests()

        val dataStore = IndexedDbDataStore.open(
            databaseName = "maryk-indexeddb-unserializable-creation-${Random.nextInt()}",
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )

        val values = CompleteMarykModel.create {
            string with "haas"
            number with 24u
            subModel with SimpleMarykModel.create {
                value with "haha"
            }
            multi with T2(22)
            booleanForKey with true
            dateForKey with LocalDate(2018, 3, 29)
            multiForKey with S1("hii")
            enumEmbedded with E1
        }

        try {
            val add = dataStore.execute(CompleteMarykModel.add(values))
            val addStatus = assertStatusIs<AddSuccess<*>>(add.statuses.single())

            val history = dataStore.execute(
                CompleteMarykModel.scanUpdateHistory(
                    fromVersion = addStatus.version,
                    toVersion = addStatus.version,
                    filterSoftDeleted = false,
                    limit = 1u,
                )
            )

            val update = assertIs<AdditionUpdate<*>>(history.updates.single())
            assertEquals(addStatus.key, update.key)
            assertEquals(addStatus.version, update.version)
        } finally {
            dataStore.close()
        }
    }

}

private class XorFieldEncryptionProvider : FieldEncryptionProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { index -> (value[offset + index].toInt() xor 0x5A).toByte() }
}

private class XorWithTokenFieldEncryptionProvider :
    FieldEncryptionProvider,
    SensitiveIndexTokenProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)

    override suspend fun deriveDeterministicToken(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray {
        val token = ByteArray(16)
        var tokenIndex = 0
        for (byte in reference) {
            token[tokenIndex % token.size] = (token[tokenIndex % token.size].toInt() xor byte.toInt() xor 0x21).toByte()
            tokenIndex++
        }
        for (index in offset until offset + length) {
            val byte = value[index]
            token[tokenIndex % token.size] = (token[tokenIndex % token.size].toInt() xor byte.toInt() xor 0x63).toByte()
            tokenIndex++
        }
        token[0] = (token[0].toInt() xor modelId.toInt()).toByte()
        return token
    }

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { index -> (value[offset + index].toInt() xor 0x5A).toByte() }
}

private class RotatingTokenFieldEncryptionProvider(
    private val activeToken: Int,
    private val previousTokens: List<Int> = emptyList(),
) : FieldEncryptionProvider, SensitiveIndexTokenProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)

    override suspend fun deriveDeterministicToken(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray = ByteArray(16) { activeToken.toByte() }

    override suspend fun deriveDeterministicTokenCandidates(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): List<ByteArray> = (listOf(activeToken) + previousTokens)
        .distinct()
        .map { token -> ByteArray(16) { token.toByte() } }

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { index -> (value[offset + index].toInt() xor 0x5A).toByte() }
}

private object SensitiveRecord : RootDataModel<SensitiveRecord>(
    keyDefinition = { SensitiveRecord.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val publicText by string(2u)
    val secret by string(3u, sensitive = true)

    operator fun invoke(id: Bytes, publicText: String, secret: String) = create {
        this.id with id
        this.publicText with publicText
        this.secret with secret
    }
}

private object SensitiveIndexedRecord : RootDataModel<SensitiveIndexedRecord>(
    keyDefinition = { SensitiveIndexedRecord.id.ref() },
    indexes = { listOf(SensitiveIndexedRecord.secret.ref()) },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val secret by string(2u, sensitive = true)
}

private object SensitiveUniqueRecord : RootDataModel<SensitiveUniqueRecord>(
    keyDefinition = { SensitiveUniqueRecord.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val secret by string(2u, unique = true, sensitive = true)

    operator fun invoke(id: Bytes, secret: String) = create {
        this.id with id
        this.secret with secret
    }
}

private val indexedDbLongTestTimeout = 3.minutes

private infix fun ByteArray.compareToBytes(other: ByteArray): Int {
    val size = minOf(size, other.size)
    for (index in 0 until size) {
        val comparison = (this[index].toInt() and 0xff) - (other[index].toInt() and 0xff)
        if (comparison != 0) return comparison
    }
    return this.size - other.size
}

private suspend fun runTestCase(
    testCase: IsDataStoreTest,
    name: String,
) {
    val test = testCase.allTests[name] ?: error("Missing datastore test `$name`.")
    testCase.initData()
    try {
        test()
    } catch (e: Throwable) {
        throw AssertionError("Shared datastore test `$name` failed: $e", e)
    } finally {
        testCase.resetData()
    }
}
