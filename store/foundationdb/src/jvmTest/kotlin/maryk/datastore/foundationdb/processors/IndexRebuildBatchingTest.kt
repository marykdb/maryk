package maryk.datastore.foundationdb.processors

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.datetime.LocalDateTime
import maryk.core.models.RootDataModel
import maryk.core.exceptions.StorageException
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.index.Multiple
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.model.beginModelSchemaRebuild
import maryk.datastore.foundationdb.model.publishModelSchemaReady
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.foundationdb.Range
import maryk.test.models.AnyValueMapIndexModel
import maryk.test.models.Option
import maryk.test.models.AnyValueSetIndexModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private object CompositeAnyIndexModel : RootDataModel<CompositeAnyIndexModel>(
    indexes = {
        listOf(
            Multiple(
                CompositeAnyIndexModel { mapValues.refToAnyKey() },
                CompositeAnyIndexModel { setValues.refToAny() },
            )
        )
    }
) {
    val name by string(index = 1u)
    val mapValues by map(
        index = 2u,
        keyDefinition = StringDefinition(maxSize = 20u),
        valueDefinition = StringDefinition(maxSize = 20u),
    )
    val setValues by set(index = 3u, valueDefinition = StringDefinition(maxSize = 20u))
}

private object LargeRowIndexModel : RootDataModel<LargeRowIndexModel>(
    indexes = { listOf(LargeRowIndexModel { name::ref }) }
) {
    val name by string(index = 1u, maxSize = 20u)
    val payload by string(index = 2u, maxSize = 90_000u)
}

private object ConcurrentRebuildModel : RootDataModel<ConcurrentRebuildModel>() {
    val name by string(index = 1u, maxSize = 20u)
}

private object HistoricMultiIndexModel : RootDataModel<HistoricMultiIndexModel>(
    indexes = {
        listOf(
            HistoricMultiIndexModel { first::ref },
            HistoricMultiIndexModel { introducedLater::ref },
        )
    }
) {
    val first by string(index = 1u, maxSize = 20u)
    val introducedLater by string(index = 2u, maxSize = 20u, required = false)
}

class IndexRebuildBatchingTest {
    @Test
    fun schemaFenceRejectsInFlightAndPostPublishOldModelWriters() = runTest(timeout = 3.minutes) {
        val directoryPath = listOf("maryk", "test", "schema-fence", Uuid.random().toString())
        val oldStore = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = directoryPath,
            dataModelsById = mapOf(1u to ConcurrentRebuildModel),
            keepAllVersions = false,
        )
        val migrator = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = directoryPath,
            dataModelsById = mapOf(1u to ConcurrentRebuildModel),
            keepAllVersions = false,
        )

        try {
            val seed = assertIs<AddSuccess<ConcurrentRebuildModel>>(
                oldStore.execute(
                    ConcurrentRebuildModel.add(ConcurrentRebuildModel.create { name with "seed" })
                ).statuses.single()
            )
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val inFlight = async(Dispatchers.IO) {
                runCatching {
                    oldStore.runTransaction(1u) { transaction ->
                        entered.countDown()
                        assertTrue(release.await(5, TimeUnit.SECONDS))
                        transaction.set(byteArrayOf(0x7f), byteArrayOf(1))
                    }
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))

            val modelPrefix = migrator.getTableDirs(ConcurrentRebuildModel).modelPrefix
            val fence = beginModelSchemaRebuild(migrator.tc, modelPrefix, ConcurrentRebuildModel)
            release.countDown()
            assertIs<StorageException>(inFlight.await().exceptionOrNull())

            assertFailsWith<StorageException> {
                oldStore.execute(ConcurrentRebuildModel.get(seed.key))
            }
            assertFailsWith<StorageException> {
                oldStore.execute(ConcurrentRebuildModel.scan())
            }

            assertIs<ServerFail<ConcurrentRebuildModel>>(
                oldStore.execute(
                    ConcurrentRebuildModel.add(ConcurrentRebuildModel.create { name with "blocked" })
                ).statuses.single()
            )

            migrator.tc.run { transaction ->
                transaction.publishModelSchemaReady(modelPrefix, fence)
            }

            assertIs<ServerFail<ConcurrentRebuildModel>>(
                oldStore.execute(
                    ConcurrentRebuildModel.add(ConcurrentRebuildModel.create { name with "still-blocked" })
                ).statuses.single()
            )

            val reopened = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = directoryPath,
                dataModelsById = mapOf(1u to ConcurrentRebuildModel),
                keepAllVersions = false,
            )
            try {
                assertIs<AddSuccess<ConcurrentRebuildModel>>(
                    reopened.execute(
                        ConcurrentRebuildModel.add(ConcurrentRebuildModel.create { name with "current" })
                    ).statuses.single()
                )
            } finally {
                reopened.close()
            }
        } finally {
            migrator.close()
            oldStore.close()
        }
    }

    @Test
    fun schemaFenceCanBeTakenOverAfterFailedRebuild() = runTest(timeout = 3.minutes) {
        val directoryPath = listOf("maryk", "test", "schema-fence-takeover", Uuid.random().toString())
        val oldStore = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = directoryPath,
            dataModelsById = mapOf(1u to ConcurrentRebuildModel),
            keepAllVersions = false,
        )
        val migrator = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = directoryPath,
            dataModelsById = mapOf(1u to ConcurrentRebuildModel),
            keepAllVersions = false,
        )

        try {
            val modelPrefix = migrator.getTableDirs(ConcurrentRebuildModel).modelPrefix
            val failedFence = beginModelSchemaRebuild(migrator.tc, modelPrefix, ConcurrentRebuildModel)
            val blocked =
                oldStore.execute(
                    ConcurrentRebuildModel.add(ConcurrentRebuildModel.create { name with "blocked" })
                ).statuses.single()
            assertIs<ServerFail<ConcurrentRebuildModel>>(blocked)

            val takeover = beginModelSchemaRebuild(migrator.tc, modelPrefix, ConcurrentRebuildModel)
            assertTrue(takeover.epoch.contentEquals(failedFence.epoch))
            assertTrue(takeover.owner != failedFence.owner)
            migrator.tc.run { transaction ->
                transaction.publishModelSchemaReady(modelPrefix, takeover)
            }
        } finally {
            migrator.close()
            oldStore.close()
        }
    }

    @Test
    fun rebuildRetriesWhenAWriterChangesTheRecordAfterMaterialization() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "concurrent-index-rebuild", Uuid.random().toString()),
            dataModelsById = mapOf(1u to ConcurrentRebuildModel),
            keepAllVersions = false,
        )

        try {
            val add = assertIs<AddSuccess<ConcurrentRebuildModel>>(
                store.execute(
                    ConcurrentRebuildModel.add(
                        ConcurrentRebuildModel.create { name with "before" }
                    )
                ).statuses.single()
            )
            val indexable = ConcurrentRebuildModel { name::ref }
            val tableDirectories = store.getTableDirs(ConcurrentRebuildModel)
            var changed = false

            walkDataRecordsAndFillIndex(
                store.tc,
                tableDirectories,
                listOf(indexable),
                ConcurrentRebuildModel,
                onRecordMaterialized = {
                    if (!changed) {
                        changed = true
                        runBlocking {
                            assertIs<ChangeSuccess<ConcurrentRebuildModel>>(
                                store.execute(
                                    ConcurrentRebuildModel.change(
                                        add.key.change(Change(ConcurrentRebuildModel { name::ref } with "after"))
                                    )
                                ).statuses.single()
                            )
                        }
                    }
                },
            )

            val rebuiltRows = store.runTransaction { tr ->
                tr.getRange(Range.startsWith(packKey(tableDirectories.indexPrefix, indexable.referenceStorageByteArray.bytes)))
                    .asList()
                    .awaitResult()
            }
            assertTrue(changed)
            assertEquals(1, rebuiltRows.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildDoesNotSwallowOwnerValidationFailureFromMutationSink() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "index-rebuild-owner-failure", Uuid.random().toString()),
            dataModelsById = mapOf(1u to CompositeAnyIndexModel),
            keepAllVersions = false,
        )

        try {
            store.execute(
                CompositeAnyIndexModel.add(CompositeAnyIndexModel.create {
                    name with "value"
                    mapValues with mapOf("first" to "value", "second" to "value")
                    setValues with setOf("present")
                })
            )
            val indexable = CompositeAnyIndexModel { mapValues.refToAnyKey() }

            assertFailsWith<StorageException> {
                walkDataRecordsAndFillIndex(
                    store.tc,
                    store.getTableDirs(CompositeAnyIndexModel),
                    listOf(indexable),
                    CompositeAnyIndexModel,
                    mutationsPerWriteTransaction = 1,
                    verifyRebuildOwner = { throw StorageException("schema transition lost") },
                )
            }
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildsIndexWhenARecordRowExceedsTheReadBudget() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "large-index-rebuild-row", Uuid.random().toString()),
            dataModelsById = mapOf(1u to LargeRowIndexModel),
            keepAllVersions = false,
        )

        try {
            store.execute(
                LargeRowIndexModel.add(
                    LargeRowIndexModel.create {
                        name with "large-row"
                        payload with "x".repeat(70 * 1024)
                    }
                )
            )
            val indexable = LargeRowIndexModel { name::ref }
            val tableDirectories = store.getTableDirs(LargeRowIndexModel)
            deleteCompleteIndexContents(store.tc, tableDirectories, indexable)

            walkDataRecordsAndFillIndex(store.tc, tableDirectories, listOf(indexable), LargeRowIndexModel)

            assertEquals(
                1,
                store.execute(
                    LargeRowIndexModel.scan(
                        where = Equals(LargeRowIndexModel { name::ref } with "large-row")
                    )
                ).values.size
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildsCompositeCollectionIndexAcrossTransactions() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "composite-index-rebuild-batches", Uuid.random().toString()),
            dataModelsById = mapOf(1u to CompositeAnyIndexModel),
            keepAllVersions = false,
        )

        try {
            store.execute(
                CompositeAnyIndexModel.add(
                    CompositeAnyIndexModel.create {
                        name with "composite"
                        mapValues with (0 until 17).associate { "map-$it" to "value-$it" }
                        setValues with (0 until 17).mapTo(linkedSetOf()) { "set-$it" }
                    }
                )
            )
            val indexable = CompositeAnyIndexModel.Meta.indexes!!.single()
            val tableDirectories = store.getTableDirs(CompositeAnyIndexModel)
            deleteCompleteIndexContents(store.tc, tableDirectories, indexable)
            val readTransactions = mutableListOf<IndexRebuildReadTransaction>()
            val writeTransactions = mutableListOf<IndexRebuildWriteTransaction>()
            var callbackObservedCommittedRow = false

            assertTrue(
                walkDataRecordsAndFillIndex(
                    store.tc,
                    tableDirectories,
                    listOf(indexable),
                    CompositeAnyIndexModel,
                    rowsPerReadTransaction = 1,
                    bytesPerReadTransaction = 256,
                    bytesPerWriteTransaction = 256,
                    onReadTransaction = readTransactions::add,
                    onWriteTransaction = { write ->
                        writeTransactions += write
                        callbackObservedCommittedRow = store.runTransaction { transaction ->
                            transaction.getRange(
                                Range.startsWith(
                                    packKey(tableDirectories.indexPrefix, indexable.referenceStorageByteArray.bytes)
                                ),
                                1,
                                false,
                            ).asList().awaitResult().isNotEmpty()
                        }
                    },
                ) > 1
            )
            assertTrue(readTransactions.isNotEmpty())
            assertTrue(readTransactions.all { it.rows <= 1 && it.bytes <= 256 })
            assertTrue(writeTransactions.isNotEmpty())
            assertTrue(writeTransactions.all { it.mutations <= INDEX_REBUILD_MUTATIONS_PER_WRITE_TRANSACTION && it.bytes <= 256 })
            assertTrue(callbackObservedCommittedRow)

            val rebuiltRows = store.runTransaction { tr ->
                tr.getRange(Range.startsWith(packKey(tableDirectories.indexPrefix, indexable.referenceStorageByteArray.bytes)))
                    .asList()
                    .awaitResult()
            }
            assertEquals(17 * 17, rebuiltRows.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildReadPagesHonorConfiguredRowLimit() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "index-rebuild-read-row-limit", Uuid.random().toString()),
            dataModelsById = mapOf(1u to CompositeAnyIndexModel),
            keepAllVersions = false,
        )

        try {
            store.execute(
                CompositeAnyIndexModel.add(
                    CompositeAnyIndexModel.create {
                        name with "paged"
                        mapValues with (0 until 4).associate { "map-$it" to "value-$it" }
                        setValues with (0 until 4).mapTo(linkedSetOf()) { "set-$it" }
                    }
                )
            )
            val indexable = CompositeAnyIndexModel { mapValues.refToAnyKey() }
            val tableDirectories = store.getTableDirs(CompositeAnyIndexModel)
            deleteCompleteIndexContents(store.tc, tableDirectories, indexable)
            val reads = mutableListOf<IndexRebuildReadTransaction>()

            walkDataRecordsAndFillIndex(
                store.tc,
                tableDirectories,
                listOf(indexable),
                CompositeAnyIndexModel,
                rowsPerReadTransaction = 3,
                bytesPerReadTransaction = 64 * 1024,
                onReadTransaction = reads::add,
            )

            assertTrue(reads.any { it.rows == 3 })
            assertTrue(reads.all { it.rows <= 3 && it.bytes <= 64 * 1024 })
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildsLargeHistoricCompositeRecordThroughSingleRowPages() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-composite-streaming-rebuild", Uuid.random().toString()),
            dataModelsById = mapOf(1u to CompositeAnyIndexModel),
            keepAllVersions = true,
        )

        try {
            val initialMap = (0 until 17).associate { "map-$it" to "value-$it" }
            val setMembers = (0 until 17).mapTo(linkedSetOf()) { "set-$it" }
            val add = assertIs<AddSuccess<CompositeAnyIndexModel>>(
                store.execute(CompositeAnyIndexModel.add(CompositeAnyIndexModel.create {
                    name with "historic-composite"
                    mapValues with initialMap
                    setValues with setMembers
                })).statuses.single()
            )
            assertIs<ChangeSuccess<CompositeAnyIndexModel>>(
                store.execute(
                    CompositeAnyIndexModel.change(
                        add.key.change(Change(CompositeAnyIndexModel { mapValues::ref } with (initialMap + ("map-17" to "value-17"))))
                    )
                ).statuses.single()
            )

            val indexable = CompositeAnyIndexModel.Meta.indexes!!.single()
            val dirs = assertIs<HistoricTableDirectories>(store.getTableDirs(CompositeAnyIndexModel))
            deleteCompleteIndexContents(store.tc, dirs, indexable)
            val reads = mutableListOf<IndexRebuildReadTransaction>()

            walkDataRecordsAndFillIndex(
                store.tc, dirs, listOf(indexable), CompositeAnyIndexModel,
                rowsPerReadTransaction = 1,
                bytesPerReadTransaction = 64,
                mutationsPerWriteTransaction = 1,
                bytesPerWriteTransaction = 64,
                historicVersionsPerTransaction = 1,
                historicCollectionEntriesPerTransaction = 1,
                onReadTransaction = reads::add,
            )

            assertTrue(reads.isNotEmpty())
            assertTrue(reads.all { it.rows <= 1 && it.bytes <= 64 })
            assertTrue(reads.count { it.rows == 1 } > 300)
            val historicRows = store.runTransaction { transaction ->
                transaction.getRange(Range.startsWith(dirs.historicIndexPrefix)).asList().awaitResult()
            }
            assertEquals(17 * 17 + 17, historicRows.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun historicScratchDoesNotReuseStateBetweenIndexes() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-multi-index-scratch", Uuid.random().toString()),
            dataModelsById = mapOf(1u to HistoricMultiIndexModel),
            keepAllVersions = true,
        )

        try {
            val add = assertIs<AddSuccess<HistoricMultiIndexModel>>(
                store.execute(
                    HistoricMultiIndexModel.add(HistoricMultiIndexModel.create { first with "initial" })
                ).statuses.single()
            )
            val introduced = assertIs<ChangeSuccess<HistoricMultiIndexModel>>(
                store.execute(
                    HistoricMultiIndexModel.change(
                        add.key.change(Change(HistoricMultiIndexModel { introducedLater::ref } with "later"))
                    )
                ).statuses.single()
            )
            val tableDirectories = store.getTableDirs(HistoricMultiIndexModel)
            HistoricMultiIndexModel.Meta.indexes!!.forEach {
                deleteCompleteIndexContents(store.tc, tableDirectories, it)
            }
            walkDataRecordsAndFillIndex(
                store.tc, tableDirectories, HistoricMultiIndexModel.Meta.indexes!!, HistoricMultiIndexModel,
                rowsPerReadTransaction = 1,
                bytesPerReadTransaction = 64,
                mutationsPerWriteTransaction = 1,
                bytesPerWriteTransaction = 64,
            )

            assertEquals(0, store.execute(
                HistoricMultiIndexModel.scan(
                    where = Equals(HistoricMultiIndexModel { introducedLater::ref } with "later"),
                    toVersion = add.version,
                )
            ).values.size)
            assertEquals(1, store.execute(
                HistoricMultiIndexModel.scan(
                    where = Equals(HistoricMultiIndexModel { introducedLater::ref } with "later"),
                    toVersion = introduced.version,
                )
            ).values.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun historicScratchDecodesEscapedMapQualifiers() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-scratch-escaped-qualifiers", Uuid.random().toString()),
            dataModelsById = mapOf(1u to AnyValueMapIndexModel),
            keepAllVersions = true,
        )
        try {
            val add = assertIs<AddSuccess<AnyValueMapIndexModel>>(
                store.execute(AnyValueMapIndexModel.add(AnyValueMapIndexModel.create {
                    name with "escaped"
                    mapValues with mapOf("\u0000" to "zero", "\u0001" to "one")
                })).statuses.single()
            )
            val indexable = AnyValueMapIndexModel { mapValues.refToAnyKey() }
            val dirs = store.getTableDirs(AnyValueMapIndexModel)
            deleteCompleteIndexContents(store.tc, dirs, indexable)
            walkDataRecordsAndFillIndex(
                store.tc, dirs, listOf(indexable), AnyValueMapIndexModel,
                rowsPerReadTransaction = 1, bytesPerReadTransaction = 64,
                mutationsPerWriteTransaction = 1, bytesPerWriteTransaction = 64,
            )
            listOf("\u0000", "\u0001").forEach { key ->
                assertEquals(1, store.execute(AnyValueMapIndexModel.scan(
                    where = Equals(AnyValueMapIndexModel { mapValues.refToAnyKey() } with key),
                    toVersion = add.version,
                )).values.size)
            }
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildsAllIndexRowsAcrossBoundedTransactions() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "index-rebuild-batches", Uuid.random().toString()),
            dataModelsById = mapOf(1u to TestMarykModel),
            keepAllVersions = false,
        )

        try {
            assertEquals(
                5,
                store.execute(
                    TestMarykModel.add(*Array(5) { index ->
                        TestMarykModel.create {
                            int with 5
                            uint with index.toUInt()
                            double with (index + 1).toDouble()
                            dateTime with LocalDateTime(2024, 1, 1, 0, index)
                            bool with (index % 2 == 0)
                            enum with Option.V1
                        }
                    })
                ).statuses.count { it is AddSuccess<TestMarykModel> }
            )

            val indexables = listOf(
                TestMarykModel { int::ref },
                TestMarykModel { uint::ref },
            )
            val tableDirectories = store.getTableDirs(TestMarykModel)
            indexables.forEach { deleteCompleteIndexContents(store.tc, tableDirectories, it) }

            assertTrue(walkDataRecordsAndFillIndex(store.tc, tableDirectories, indexables, TestMarykModel) > 0)
            assertEquals(
                5,
                store.execute(
                    TestMarykModel.scan(
                        where = Equals(TestMarykModel { int::ref } with 5)
                    )
                ).values.size
            )
            assertEquals(
                1,
                store.execute(
                    TestMarykModel.scan(
                        where = Equals(TestMarykModel { uint::ref } with 2u)
                    )
                ).values.size
            )

        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildsHistoricIndexInMultipleTransactionsForOneDeepHistoryRecord() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-index-rebuild-batches", Uuid.random().toString()),
            dataModelsById = mapOf(1u to TestMarykModel),
            keepAllVersions = true,
        )

        try {
            val add = assertIs<AddSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.add(
                        TestMarykModel.create {
                            int with 1
                            uint with 1u
                            double with 1.0
                            dateTime with LocalDateTime(2024, 1, 1, 0, 0)
                            bool with true
                            enum with Option.V1
                        }
                    )
                ).statuses.single()
            )
            val changes = (2..5).map { value ->
                assertIs<ChangeSuccess<TestMarykModel>>(
                    store.execute(
                        TestMarykModel.change(add.key.change(Change(TestMarykModel { int::ref } with value)))
                    ).statuses.single()
                )
            }

            val indexable = TestMarykModel { int::ref }
            val tableDirectories = store.getTableDirs(TestMarykModel)
            deleteCompleteIndexContents(store.tc, tableDirectories, indexable)

            val transactions = walkDataRecordsAndFillIndex(
                store.tc,
                tableDirectories,
                listOf(indexable),
                TestMarykModel,
                historicVersionsPerTransaction = 1,
            )

            assertTrue(transactions >= 6)
            assertEquals(1, store.execute(TestMarykModel.scan(where = Equals(TestMarykModel { int::ref } with 1), toVersion = add.version)).values.size)
            changes.forEachIndexed { index, change ->
                assertEquals(
                    1,
                    store.execute(
                        TestMarykModel.scan(
                            where = Equals(TestMarykModel { int::ref } with index + 2),
                            toVersion = change.version,
                        )
                    ).values.size
                )
            }
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildsLargeCurrentSetIndexAcrossTransactions() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "current-set-index-rebuild-batches", Uuid.random().toString()),
            dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            keepAllVersions = false,
        )

        try {
            store.execute(
                AnyValueSetIndexModel.add(
                    AnyValueSetIndexModel.create {
                        name with "large-set"
                        setValues with (0 until 33).mapTo(linkedSetOf()) { "tag-$it" }
                    }
                )
            )
            val indexable = AnyValueSetIndexModel { setValues.refToAny() }
            val tableDirectories = store.getTableDirs(AnyValueSetIndexModel)
            deleteCompleteIndexContents(store.tc, tableDirectories, indexable)

            assertEquals(3, walkDataRecordsAndFillIndex(store.tc, tableDirectories, listOf(indexable), AnyValueSetIndexModel))
            assertEquals(
                1,
                store.execute(
                    AnyValueSetIndexModel.scan(
                        where = Equals(AnyValueSetIndexModel { setValues.refToAny() } with "tag-32")
                    )
                ).values.size
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildsDeepSetHistoryAcrossTransactions() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-set-index-rebuild-batches", Uuid.random().toString()),
            dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            keepAllVersions = true,
        )

        try {
            val add = assertIs<AddSuccess<AnyValueSetIndexModel>>(
                store.execute(
                    AnyValueSetIndexModel.add(
                        AnyValueSetIndexModel.create {
                            name with "historic-set"
                            setValues with setOf("tag")
                        }
                    )
                ).statuses.single()
            )
            val changes = (1..20).map { index ->
                assertIs<ChangeSuccess<AnyValueSetIndexModel>>(
                    store.execute(
                        AnyValueSetIndexModel.change(
                            add.key.change(
                                Change(
                                    AnyValueSetIndexModel { setValues::ref } with
                                        if (index % 2 == 0) setOf("tag") else emptySet()
                                )
                            )
                        )
                    ).statuses.single()
                )
            }

            val indexable = AnyValueSetIndexModel { setValues.refToAny() }
            val tableDirectories = store.getTableDirs(AnyValueSetIndexModel)
            deleteCompleteIndexContents(store.tc, tableDirectories, indexable)

            assertTrue(
                walkDataRecordsAndFillIndex(
                    store.tc,
                    tableDirectories,
                    listOf(indexable),
                    AnyValueSetIndexModel,
                    historicCollectionEntriesPerTransaction = 1,
                ) >= 20
            )
            assertEquals(
                1,
                store.execute(
                    AnyValueSetIndexModel.scan(
                        where = Equals(AnyValueSetIndexModel { setValues.refToAny() } with "tag"),
                        toVersion = add.version,
                    )
                ).values.size
            )
            assertEquals(
                0,
                store.execute(
                    AnyValueSetIndexModel.scan(
                        where = Equals(AnyValueSetIndexModel { setValues.refToAny() } with "tag"),
                        toVersion = changes.first().version,
                    )
                ).values.size
            )
            assertEquals(
                1,
                store.execute(
                    AnyValueSetIndexModel.scan(
                        where = Equals(AnyValueSetIndexModel { setValues.refToAny() } with "tag"),
                        toVersion = changes.last().version,
                    )
                ).values.size
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildsDeepMapKeyHistoryAcrossTransactions() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-map-index-rebuild-batches", Uuid.random().toString()),
            dataModelsById = mapOf(1u to AnyValueMapIndexModel),
            keepAllVersions = true,
        )

        try {
            val add = assertIs<AddSuccess<AnyValueMapIndexModel>>(
                store.execute(
                    AnyValueMapIndexModel.add(
                        AnyValueMapIndexModel.create {
                            name with "historic-map"
                            mapValues with mapOf("key" to "value")
                        }
                    )
                ).statuses.single()
            )
            val changes = (1..21).map { index ->
                assertIs<ChangeSuccess<AnyValueMapIndexModel>>(
                    store.execute(
                        AnyValueMapIndexModel.change(
                            add.key.change(
                                Change(
                                    AnyValueMapIndexModel { mapValues::ref } with
                                        if (index % 2 == 0) mapOf("key" to "value") else emptyMap()
                                )
                            )
                        )
                    ).statuses.single()
                )
            }

            val indexable = AnyValueMapIndexModel { mapValues.refToAnyKey() }
            val tableDirectories = store.getTableDirs(AnyValueMapIndexModel)
            deleteCompleteIndexContents(store.tc, tableDirectories, indexable)

            assertTrue(
                walkDataRecordsAndFillIndex(
                    store.tc,
                    tableDirectories,
                    listOf(indexable),
                    AnyValueMapIndexModel,
                    historicCollectionEntriesPerTransaction = 1,
                ) >= 21
            )
            assertEquals(
                1,
                store.execute(
                    AnyValueMapIndexModel.scan(
                        where = Equals(AnyValueMapIndexModel { mapValues.refToAnyKey() } with "key"),
                        toVersion = add.version,
                    )
                ).values.size
            )
            assertEquals(
                0,
                store.execute(
                    AnyValueMapIndexModel.scan(
                        where = Equals(AnyValueMapIndexModel { mapValues.refToAnyKey() } with "key"),
                    )
                ).values.size
            )
            assertEquals(
                0,
                store.execute(
                    AnyValueMapIndexModel.scan(
                        where = Equals(AnyValueMapIndexModel { mapValues.refToAnyKey() } with "key"),
                        toVersion = changes.first().version,
                    )
                ).values.size
            )
            assertEquals(
                0,
                store.execute(
                    AnyValueMapIndexModel.scan(
                        where = Equals(AnyValueMapIndexModel { mapValues.refToAnyKey() } with "key"),
                        toVersion = changes.last().version,
                    )
                ).values.size
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun rebuildsMapKeyValueUpdatesWithoutDuplicateHistoricIndexEntries() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-map-index-rebuild-updates", Uuid.random().toString()),
            dataModelsById = mapOf(1u to AnyValueMapIndexModel),
            keepAllVersions = true,
        )

        try {
            val add = assertIs<AddSuccess<AnyValueMapIndexModel>>(
                store.execute(
                    AnyValueMapIndexModel.add(
                        AnyValueMapIndexModel.create {
                            name with "historic-map-updates"
                            mapValues with mapOf("key" to "value-0")
                        }
                    )
                ).statuses.single()
            )
            val changes = (1..20).map { index ->
                assertIs<ChangeSuccess<AnyValueMapIndexModel>>(
                    store.execute(
                        AnyValueMapIndexModel.change(
                            add.key.change(
                                Change(AnyValueMapIndexModel { mapValues.refAt("key") } with "value-$index")
                            )
                        )
                    ).statuses.single()
                )
            }

            val indexable = AnyValueMapIndexModel { mapValues.refToAnyKey() }
            val tableDirectories = store.getTableDirs(AnyValueMapIndexModel)
            deleteCompleteIndexContents(store.tc, tableDirectories, indexable)

            assertTrue(
                walkDataRecordsAndFillIndex(
                    store.tc,
                    tableDirectories,
                    listOf(indexable),
                    AnyValueMapIndexModel,
                    historicCollectionEntriesPerTransaction = 1,
                ) > 0
            )
            listOf(add.version, changes.first().version, changes.last().version).forEach { version ->
                assertEquals(
                    1,
                    store.execute(
                        AnyValueMapIndexModel.scan(
                            where = Equals(AnyValueMapIndexModel { mapValues.refToAnyKey() } with "key"),
                            toVersion = version,
                        )
                    ).values.size
                )
            }
        } finally {
            store.close()
        }
    }
}
