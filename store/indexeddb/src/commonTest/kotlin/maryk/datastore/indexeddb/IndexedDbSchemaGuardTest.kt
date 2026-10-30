package maryk.datastore.indexeddb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import maryk.core.clock.HLC
import maryk.core.exceptions.RequestException
import maryk.core.models.key
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.MigrationOutcome
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.TransactionException
import maryk.datastore.shared.TransactionalDataStore
import maryk.datastore.shared.beginTransaction
import maryk.datastore.shared.executeAtomic
import maryk.datastore.shared.executeTransaction
import maryk.test.models.ModelV1_1
import maryk.test.models.ModelV2
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IndexedDbSchemaGuardTest {
    @Test
    fun staleOrdinaryMutationsDoNotChangePersistedState() = runTest {
        installIndexedDbForTests()
        checkStaleOrdinaryMutations()
    }

    @Test
    fun leaseRejectsStaleOrdinaryMutations() = runTest {
        installIndexedDbForTests()
        withoutWebLocks { checkStaleOrdinaryMutations() }
    }

    private suspend fun checkStaleOrdinaryMutations() = withOriginalStore { database, old, key ->
        val current = openMigrated(database)
        try {
            val before = persistedMutationState(current)
            assertReopenRequired { old.execute(ModelV1_1.add(ModelV1_1.create { value with "ha stale add" })) }
            assertReopenRequired { old.execute(ModelV1_1.change(key.change(Change(ModelV1_1.ref { value } with "ha stale change")))) }
            assertReopenRequired { old.execute(ModelV1_1.delete(key)) }
            assertEquals(before, persistedMutationState(current))
            assertEquals("ha original", current.execute(ModelV2.get(Key<ModelV2>(key.bytes))).values.single().values { value })
            val reopened = IndexedDbDataStore.open(database, mapOf(1u to ModelV2), keepAllVersions = true)
            try {
                assertIs<AddSuccess<ModelV2>>(reopened.execute(
                    ModelV2.add(ModelV2.create { value with "ha current"; newNumber with 8 }),
                ).statuses.single())
            } finally {
                reopened.close()
            }
        } finally {
            current.close()
        }
    }

    @Test
    fun staleReplicationAndInitialChangesDoNotWrite() = runTest {
        installIndexedDbForTests()
        withOriginalStore { database, old, _ ->
            val current = openMigrated(database)
            try {
                val values = ModelV1_1.create { value with "ha stale import" }
                val key = ModelV1_1.key(values)
                val version = HLC().timestamp
                val before = persistedMutationState(current)
                assertReopenRequired {
                    old.processUpdate(UpdateResponse(ModelV1_1, AdditionUpdate(key, version, version, 0, false, values)))
                }
                assertReopenRequired {
                    old.processUpdate(UpdateResponse(ModelV1_1, InitialChangesUpdate(version + 1uL, listOf(
                        DataObjectVersionedChange(key, changes = listOf(VersionedChanges(version, listOf(ObjectCreate) + values.toChanges()))),
                    ))))
                }
                assertEquals(before, persistedMutationState(current))
            } finally {
                current.close()
            }
        }
    }

    @Test
    fun staleAtomicAndStagedTransactionRejectWithoutUncertainOutcome() = runTest {
        installIndexedDbForTests()
        withOriginalStore { database, old, key ->
            val staged = old.beginTransaction()
            staged.execute(ModelV1_1.change(key.change(Change(ModelV1_1.ref { value } with "ha staged"))))
            val current = openMigrated(database)
            try {
                val before = persistedMutationState(current)
                val atomic = old.executeAtomic(AtomicRequest("stale-atomic",
                    ModelV1_1.add(ModelV1_1.create { value with "ha stale atomic" }),
                ))
                assertEquals(AtomicStatus.Rejected, atomic.status)
                assertEquals(AtomicFailureCode.InvalidState, atomic.failure?.code)
                assertTrue(atomic.message.orEmpty().contains("reopen"))
                val commit = staged.commit()
                assertEquals(TransactionStatus.Aborted, commit.status)
                assertEquals(AtomicFailureCode.InvalidState, commit.failure?.code)
                // Capturing a new transaction snapshot is itself a read and must
                // reject a stale schema before any operation can be staged.
                val failure = assertFailsWith<TransactionException> {
                    old.beginTransaction()
                }
                assertEquals(TransactionStatus.Aborted, failure.response.status)
                assertEquals(AtomicFailureCode.InvalidState, failure.response.failure?.code)
                assertEquals(before, persistedMutationState(current))
            } finally {
                current.close()
            }
        }
    }

    @Test
    fun committedReceiptsResolveAfterSchemaChanges() = runTest {
        installIndexedDbForTests()
        withOriginalStore { database, old, key ->
            val atomicRequest = AtomicRequest("committed-before-migration",
                ModelV1_1.change(key.change(Change(ModelV1_1.ref { value } with "ha atomic committed"))),
            )
            val atomicCommit = old.executeAtomic(atomicRequest)
            assertEquals(AtomicStatus.Committed, atomicCommit.status)
            var loseResponse = true
            val transport = object : IsDataStore by old, TransactionalDataStore {
                override val transactionCapabilities get() = old.transactionCapabilities
                override suspend fun executeTransaction(request: TransactionRequest): TransactionResponse {
                    val response = old.executeTransaction(request)
                    if (request.commit && loseResponse) {
                        loseResponse = false
                        error("Lost commit response")
                    }
                    return response
                }
            }
            val transaction = transport.beginTransaction()
            transaction.execute(ModelV1_1.change(key.change(Change(ModelV1_1.ref { value } with "ha transaction committed"))))
            assertEquals(TransactionStatus.OutcomeUnknown, transaction.commit().status)
            val current = openMigrated(database)
            try {
                val before = persistedMutationState(current)
                assertEquals(atomicCommit, old.executeAtomic(atomicRequest))
                assertEquals(TransactionStatus.Committed, transaction.resolveCommit().status)
                val mismatch = old.executeTransaction(TransactionRequest(transaction.id, commit = true))
                assertEquals(TransactionStatus.Aborted, mismatch.status)
                assertEquals(AtomicFailureCode.ChangeSetMismatch, mismatch.failure?.code)
                assertEquals(before, persistedMutationState(current))
            } finally {
                current.close()
            }
        }
    }

    @Test
    fun unchangedSchemaContextsCanStillMutate() = runTest {
        installIndexedDbForTests()
        withOriginalStore { database, first, key ->
            val second = IndexedDbDataStore.open(database, mapOf(1u to ModelV1_1), keepAllVersions = true)
            try {
                assertIs<AddSuccess<ModelV1_1>>(first.execute(
                    ModelV1_1.add(ModelV1_1.create { value with "ha unchanged schema" }),
                ).statuses.single())
                assertEquals(AtomicStatus.Committed, second.executeAtomic(AtomicRequest("same-schema",
                    ModelV1_1.change(key.change(Change(ModelV1_1.ref { value } with "ha other context"))),
                )).status)
            } finally {
                second.close()
            }
        }
    }

    @Test
    fun pendingMigrationBlocksWritesBeforeDefinitionChanges() = runTest {
        installIndexedDbForTests()
        withOriginalStore { database, old, key ->
            assertFailsWith<RequestException> {
                openMigrated(database, MigrationConfiguration(migrationHandler = {
                    MigrationOutcome.Partial(byteArrayOf(1), "resume migration")
                }))
            }
            val before = persistedMutationState(old)
            assertReopenRequired { old.execute(ModelV1_1.delete(key)) }
            val atomic = old.executeAtomic(AtomicRequest("pending-migration", ModelV1_1.delete(key)))
            assertEquals(AtomicStatus.Rejected, atomic.status)
            assertEquals(AtomicFailureCode.InvalidState, atomic.failure?.code)
            assertEquals(before, persistedMutationState(old))
            var callbackRan = false
            val current = openMigrated(database, MigrationConfiguration(migrationHandler = { context ->
                val currentKey = Key<ModelV2>(key.bytes)
                assertEquals("ha original", context.store.execute(ModelV2.get(currentKey)).values.single().values { value })
                assertIs<ChangeSuccess<ModelV2>>(context.store.execute(ModelV2.change(
                    currentKey.change(Change(ModelV2.ref { newNumber } with 8)),
                )).statuses.single())
                callbackRan = true
                MigrationOutcome.Success
            }))
            try {
                assertTrue(callbackRan)
                assertIs<AddSuccess<ModelV2>>(current.execute(
                    ModelV2.add(ModelV2.create { value with "ha resumed"; newNumber with 9 }),
                ).statuses.single())
            } finally {
                current.close()
            }
        }
    }

    @Test
    fun pendingMigrationCannotBeBypassedByUnrelatedStartupCallback() = runTest {
        installIndexedDbForTests()
        withOriginalStore { database, old, key ->
            assertFailsWith<RequestException> {
                openMigrated(database, MigrationConfiguration(migrationHandler = { MigrationOutcome.Partial(byteArrayOf(1)) }))
            }
            var callbackRan = false
            var unrelatedOpen: IndexedDbDataStore? = null
            try {
                assertReopenRequired {
                    unrelatedOpen = IndexedDbDataStore.open(
                        database,
                        // The new model's callback would run before the pending model without a preflight.
                        mapOf(2u to SimpleMarykModel, 1u to ModelV1_1),
                        keepAllVersions = true,
                        versionUpdateHandler = { store, _, _ ->
                            callbackRan = true
                            store.execute(ModelV1_1.change(key.change(Change(ModelV1_1.ref { value } with "ha bypassed"))))
                        },
                    )
                }
                assertFalse(callbackRan)
            } finally {
                unrelatedOpen?.close()
            }
            val current = openMigrated(database)
            try {
                assertEquals("ha original", current.execute(ModelV2.get(Key<ModelV2>(key.bytes))).values.single().values { value })
            } finally {
                current.close()
            }
        }
    }

    @Test
    fun staleTombstoneCompactionDoesNotRemoveReplicationState() = runTest {
        installIndexedDbForTests()
        withOriginalStore { database, old, key ->
            old.execute(ModelV1_1.delete(key, hardDelete = true))
            val current = openMigrated(database)
            try {
                assertTrue(current.byteStore.scan("hd:1").isNotEmpty())
                val before = persistedMutationState(current)
                assertReopenRequired { old.compactReplicationTombstones(ULong.MAX_VALUE) }
                assertEquals(before, persistedMutationState(current))
                current.compactReplicationTombstones(ULong.MAX_VALUE)
                assertTrue(current.byteStore.scan("hd:1").isEmpty())
            } finally {
                current.close()
            }
        }
    }

    @Test
    fun mutationQueuedBehindMigrationChecksSchemaAfterWriterFence() = runTest {
        installIndexedDbForTests()
        withOriginalStore { database, old, _ ->
            val migrationStarted = CompletableDeferred<Unit>()
            val continueMigration = CompletableDeferred<Unit>()
            val migration = async {
                openMigrated(database, MigrationConfiguration(migrationHandler = {
                    migrationStarted.complete(Unit)
                    continueMigration.await()
                    MigrationOutcome.Success
                }))
            }
            migrationStarted.await()
            val write = async {
                assertReopenRequired { old.execute(ModelV1_1.add(ModelV1_1.create { value with "ha queued" })) }
            }
            yield()
            assertFalse(write.isCompleted)
            continueMigration.complete(Unit)
            val current = migration.await()
            try {
                val before = persistedMutationState(current)
                write.await()
                assertEquals(before, persistedMutationState(current))
            } finally {
                current.close()
            }
        }
    }

    private suspend fun withOriginalStore(block: suspend (String, IndexedDbDataStore, Key<ModelV1_1>) -> Unit) =
        withoutBroadcastChannelForTests {
            val wasPaused = indexedDbJournalPollingPausedForTests
            indexedDbJournalPollingPausedForTests = true
            val database = "maryk-schema-guard-${Random.nextInt()}"
            val store = IndexedDbDataStore.open(database, mapOf(1u to ModelV1_1), keepAllVersions = true)
            try {
                val added = assertIs<AddSuccess<ModelV1_1>>(store.execute(ModelV1_1.add(
                    ModelV1_1.create { value with "ha original"; newNumber with 7 },
                )).statuses.single())
                block(database, store, added.key)
            } finally {
                store.close()
                indexedDbJournalPollingPausedForTests = wasPaused
            }
        }

    private suspend fun openMigrated(
        database: String,
        configuration: MigrationConfiguration<IndexedDbDataStore> = MigrationConfiguration(migrationHandler = { MigrationOutcome.Success }),
    ) = IndexedDbDataStore.open(database, mapOf(1u to ModelV2), keepAllVersions = true, migrationConfiguration = configuration)

    private suspend fun assertReopenRequired(block: suspend () -> Unit) {
        val error = assertFailsWith<RequestException> { block() }
        assertTrue(error.message.orEmpty().contains("reopen"))
    }

    /** Exclude writer-lease/consumer bookkeeping; retain all data, indexes, journal, clock, revision and receipts. */
    private suspend fun persistedMutationState(store: IndexedDbDataStore): Map<String, List<Pair<List<Byte>, List<Byte>>>> {
        val stores = listOf("k:1", "t:1", "i:1", "u:1", "c:1", "hd:1", "ht:1", "hi:1", "hu:1", "hik:1", "huk:1", CommitJournalStoreName)
        return stores.associateWith { name ->
            store.byteStore.scan(name).map { (key, value) -> key.toList() to value.toList() }
        } + ("meta" to store.byteStore.scan("meta").filter { (key, _) ->
            key.contentEquals(CommitClockMetadataKey) || key.contentEquals(TransactionRevisionKey) ||
                key.decodeToString().startsWith("maryk.transaction/")
        }.map { (key, value) -> key.toList() to value.toList() })
    }
}
