@file:Suppress("unused")

package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.MigrationAuditEvent
import maryk.core.models.migration.MigrationAuditEventType
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationLease
import maryk.core.models.migration.MigrationOutcome
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationRetryPolicy
import maryk.core.models.migration.MigrationRuntimeState
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.models.migration.NoopMigrationLease
import maryk.datastore.foundationdb.model.modelMigrationStateKey
import maryk.datastore.foundationdb.model.modelMigrationLeaseKey
import maryk.datastore.foundationdb.model.modelMigrationAuditLogKey
import maryk.datastore.foundationdb.model.FoundationDBMigrationLease
import maryk.datastore.foundationdb.model.FoundationDBMigrationLeaseLostException
import maryk.datastore.foundationdb.model.FoundationDBMigrationStateStore
import maryk.datastore.foundationdb.model.FoundationDBMigrationAuditLogStore
import maryk.datastore.foundationdb.model.beginModelSchemaRebuild
import maryk.datastore.foundationdb.model.modelSchemaStateKey
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.model.modelVersionKey
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.SInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.datastore.test.dataModelsForTests
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext
import maryk.test.models.ModelV1
import maryk.test.models.ModelV1_1
import maryk.test.models.ModelV2
import maryk.test.models.ModelV2ExtraIndex
import maryk.test.models.ModelWithDependents
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class FoundationDBDataStoreMigrationTest {
    @Test
    fun legacyRootOnlyRebuildTargetResumesWithoutDependents() = runTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "legacy-rebuild-target", Uuid.random().toString()),
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        try {
            val prefix = store.getTableDirs(1u).modelPrefix
            val fence = beginModelSchemaRebuild(store.tc, prefix, SimpleMarykModel)
            store.runTransaction { transaction ->
                val key = packKey(prefix, modelSchemaStateKey)
                val state = transaction.get(key).awaitResult()!!.decodeToString()
                transaction.set(key, state.replace("target=${fence.target}", "target=${fence.target.substringBeforeLast(':')}").encodeToByteArray())
            }

            beginModelSchemaRebuild(store.tc, prefix, SimpleMarykModel)
        } finally {
            store.close()
        }
    }

    class CustomException : Error()

    @Test
    fun migrationAuditReporterAndMetricsDoNotRequirePersistence() = runTest(timeout = 3.minutes) {
        for (persistAudit in listOf(false, true)) {
            val directory = listOf("maryk", "test", "migration-audit-reporting", Uuid.random().toString())
            FoundationDBDataStore.open(
                directoryPath = directory,
                dataModelsById = mapOf(1u to ModelV1_1),
            ).close()
            val reported = mutableListOf<MigrationAuditEvent>()
            var attempts = 0
            val store = FoundationDBDataStore.open(
                directoryPath = directory,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    persistMigrationAuditEvents = persistAudit,
                    migrationAuditEventReporter = reported::add,
                    migrationHandler = {
                        when (++attempts) {
                            1 -> MigrationOutcome.Partial()
                            2 -> MigrationOutcome.Retry()
                            else -> MigrationOutcome.Success
                        }
                    },
                ),
            )
            try {
                val types = reported.map { it.type }
                assertEquals(1, types.count { it == MigrationAuditEventType.LeaseAcquired })
                assertEquals(1, types.count { it == MigrationAuditEventType.Completed })
                assertEquals(1, types.count { it == MigrationAuditEventType.Partial })
                assertEquals(1, types.count { it == MigrationAuditEventType.RetryScheduled })
                assertEquals(4, types.count { it == MigrationAuditEventType.PhaseCompleted })
                assertEquals(1u, store.migrationMetrics(1u).partials)
                assertEquals(1u, store.migrationMetrics(1u).retries)
                assertEquals(1u, store.migrationMetrics(1u).completed)
                val persisted = store.migrationAuditEvents(1u)
                assertEquals(if (persistAudit) reported else emptyList(), persisted)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun migrationStateAndAuditEventCommitTogether() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = listOf("maryk", "test", "atomic-migration-audit", Uuid.random().toString()),
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        try {
            val modelPrefix = store.getTableDirs(1u).modelPrefix
            val stateStore = FoundationDBMigrationStateStore(store.tc, mapOf(1u to modelPrefix))
            val auditStore = FoundationDBMigrationAuditLogStore(store.tc, mapOf(1u to modelPrefix))
            val state = MigrationState(
                migrationId = "audit-atomicity",
                phase = MigrationPhase.Backfill,
                status = MigrationStateStatus.Running,
                attempt = 1u,
                fromVersion = "1.0",
                toVersion = "2.0",
            )
            val event = MigrationAuditEvent(
                timestampMs = 1L,
                modelId = 1u,
                migrationId = state.migrationId,
                type = MigrationAuditEventType.Partial,
                phase = state.phase,
                attempt = state.attempt,
            )

            stateStore.writeWithAudit(1u, state, auditStore, event)

            assertEquals(state, stateStore.read(1u))
            assertEquals(listOf(event), auditStore.read(1u))
        } finally {
            store.close()
        }
    }

    @Test
    fun newModelDefinitionIsPublishedOnlyAfterVersionHandlerSucceeds() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-new-model-handler-publication", Uuid.random().toString())

        assertFailsWith<IllegalStateException> {
            FoundationDBDataStore.open(
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV1),
                versionUpdateHandler = { _, _, _ -> error("first handler failure") },
            )
        }

        var retryCalls = 0
        FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1),
            versionUpdateHandler = { _, oldModel, _ ->
                assertNull(oldModel)
                retryCalls++
            },
        ).close()

        assertEquals(1, retryCalls)
    }

    @Test
    fun safeAddDefinitionIsPublishedOnlyAfterVersionHandlerSucceeds() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-safe-add-handler-publication", Uuid.random().toString())
        FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1),
        ).close()

        assertFailsWith<IllegalStateException> {
            FoundationDBDataStore.open(
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV1_1),
                versionUpdateHandler = { _, _, _ -> error("first handler failure") },
            )
        }

        var retryCalls = 0
        FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1),
            versionUpdateHandler = { _, oldModel, _ ->
                assertNotNull(oldModel)
                retryCalls++
            },
        ).close()

        assertEquals(1, retryCalls)
    }

    @Test
    fun newIndexDefinitionIsPublishedOnlyAfterVersionHandlerSucceeds() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-index-handler-publication", Uuid.random().toString())
        FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
        ).close()

        assertFailsWith<IllegalStateException> {
            FoundationDBDataStore.open(
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV2ExtraIndex),
                versionUpdateHandler = { _, _, _ -> error("first handler failure") },
            )
        }

        var retryCalls = 0
        FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2ExtraIndex),
            versionUpdateHandler = { _, oldModel, _ ->
                assertNotNull(oldModel)
                retryCalls++
            },
        ).close()

        assertEquals(1, retryCalls)
    }

    @Test
    fun newIndexVersionHookAllowsOwnerWritesWhileLegacyWritersRemainFenced() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-index-handler-fence", Uuid.random().toString())
        FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
        ).close()

        val hookStarted = CompletableDeferred<Unit>()
        val releaseHook = CompletableDeferred<Unit>()
        val upgrading = async(Dispatchers.Default) {
            FoundationDBDataStore.open(
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV2ExtraIndex),
                versionUpdateHandler = { store, _, _ ->
                    hookStarted.complete(Unit)
                    releaseHook.await()
                    val response = store.execute(
                        ModelV2ExtraIndex.add(ModelV2ExtraIndex.create {
                            value with "ha-owner"
                            newNumber with 2
                        })
                    )
                    assertIs<AddSuccess<ModelV2ExtraIndex>>(response.statuses.single())
                },
            )
        }

        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000.milliseconds) { hookStarted.await() }
        }
        val legacyStore = FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
        )
        try {
            val response = legacyStore.execute(
                ModelV2.add(ModelV2.create {
                    value with "ha-legacy"
                    newNumber with 1
                })
            )
            val failure = assertIs<ServerFail<ModelV2>>(response.statuses.single())
            assertTrue(failure.reason.contains("Model schema is rebuilding"))
        } finally {
            legacyStore.close()
            releaseHook.complete(Unit)
        }

        val upgradedStore = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000.milliseconds) { upgrading.await() }
        }
        try {
            assertEquals(
                1,
                upgradedStore.execute(
                    ModelV2ExtraIndex.scan(order = ModelV2ExtraIndex { newNumber::ref }.ascending())
                ).values.size,
            )
        } finally {
            upgradedStore.close()
        }
    }

    @Test
    fun backgroundMigrationFailsPromptlyForInvalidPersistedState() = runTest(timeout = 3.minutes) {
        val invalidStates = listOf(
            "malformed" to "not migration state".encodeToByteArray(),
            "mismatched" to MigrationState(
                migrationId = "Model:1.0->2.0",
                phase = MigrationPhase.Backfill,
                status = MigrationStateStatus.Retry,
                attempt = 1u,
                fromVersion = "1.0",
                toVersion = "2.0",
            ).toPersistedBytes(),
        )

        for ((name, state) in invalidStates) {
            val dirPath = listOf("maryk", "test", "fdb-migration-invalid-state-$name", Uuid.random().toString())
            val initialStore = FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV1_1),
            )
            initialStore.runTransaction { transaction ->
                transaction.set(packKey(initialStore.getTableDirs(1u).modelPrefix, modelMigrationStateKey), state)
            }
            initialStore.close()

            val dataStore = FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationLease = NoopMigrationLease,
                    migrationStartupBudgetMs = -1L,
                    continueMigrationsInBackground = true,
                    migrationHandler = { MigrationOutcome.Success },
                )
            )

            try {
                val failure = runCatching {
                    withContext(Dispatchers.Default.limitedParallelism(1)) {
                        withTimeout(5_000.milliseconds) {
                            dataStore.awaitMigration(1u)
                        }
                    }
                }.exceptionOrNull()

                assertIs<MigrationException>(failure)
                assertTrue(dataStore.pendingMigrations()[1u]?.contains("Persisted migration state") == true)
            } finally {
                dataStore.close()
            }
        }
    }

    @Test
    fun testComplexMigrationCheckWithNoChange() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-nochange", Uuid.random().toString())

        var dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = dataModelsForTests
        )

        dataStore.close()

        dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = dataModelsForTests
        )

        dataStore.close()
    }

    @Test
    fun testMigration() = runTest(timeout = 5.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration", Uuid.random().toString())
        var didRunUpdateHandler = false

        var dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to ModelV1,
            ),
            versionUpdateHandler = { _, oldModel, newModel ->
                didRunUpdateHandler = true
                assertNull(oldModel)
                assertEquals(ModelV1, newModel)
            }
        )

        assertTrue { didRunUpdateHandler }

        dataStore.close()

        didRunUpdateHandler = false

        dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to ModelV1_1,
            ),
            versionUpdateHandler = { _, oldModel, newModel ->
                didRunUpdateHandler = true
                assertNotNull(oldModel)
                assertEquals(ModelV1_1, newModel)
            }
        )

        assertTrue { didRunUpdateHandler }

        dataStore.close()

        assertFailsWith<MigrationException> {
            // Missing migration handler so will throw exception
            FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(
                    1u to ModelV2,
                ),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = null,
                )
            )
        }

        assertFailsWith<CustomException> {
            FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(
                    1u to ModelV2,
                ),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { context ->
                        val storedDataModel = context.storedDataModel
                        val newDataModel = context.newDataModel
                        assertEquals(ModelV2, newDataModel)
                        assertEquals(ModelV1_1.Meta.version, storedDataModel.Meta.version)
                        // Should throw this exception to proof it is entering this handler
                        throw CustomException()
                    },
                )
            )
        }
    }

    @Test
    fun migrationRunsAllPhaseHooksInOrder() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-hook-phases", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val phases = mutableListOf<String>()
        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationExpandHandler = { _ ->
                    phases += "expand"
                    MigrationOutcome.Success
                },
                migrationHandler = { _ ->
                    phases += "backfill"
                    MigrationOutcome.Success
                },
                migrationVerifyHandler = { _ ->
                    phases += "verify"
                    MigrationOutcome.Success
                },
                migrationContractHandler = { _ ->
                    phases += "contract"
                    MigrationOutcome.Success
                },
            )
        ).close()

        assertEquals(listOf("expand", "backfill", "verify", "contract"), phases)
    }

    @Test
    fun migrationCanRunWithExpandHandlerOnly() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-expand-only", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1),
        ).close()

        var expandCalls = 0
        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationExpandHandler = {
                    expandCalls++
                    MigrationOutcome.Success
                },
            ),
        ).close()

        assertEquals(1, expandCalls)
    }

    @Test
    fun startupFinalizationKeepsLeaseUntilVersionHandlerCompletes() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-finalization-lease", Uuid.random().toString())
        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1),
        ).close()

        val lease = TrackingMigrationLease()
        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationLease = lease,
                migrationHandler = { MigrationOutcome.Success },
            ),
            versionUpdateHandler = { store, oldModel, _ ->
                if (oldModel != null) {
                    assertTrue(lease.isHeld)
                    withContext(Dispatchers.Default.limitedParallelism(1)) {
                        withTimeout(5_000.milliseconds) {
                            store.execute(ModelV2.scan(order = ModelV2 { value::ref }.ascending()))
                        }
                    }
                }
            },
        ).close()

        assertEquals(1, lease.releaseCalls.value)
    }

    @Test
    fun ownershipReplacementCancelsBlockedHandlerBeforeFurtherWrites() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-ownership-loss", Uuid.random().toString())
        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1),
        ).close()

        val handlerStarted = CompletableDeferred<Unit>()
        val handlerCancelled = CompletableDeferred<Unit>()
        val ownerStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationLeaseConfiguration = FoundationDBMigrationLeaseConfiguration(
                migrationLeaseTimeoutMs = 1_000,
                migrationLeaseHeartbeatMs = 50,
            ),
            migrationConfiguration = MigrationConfiguration(
                migrationStartupBudgetMs = -1L,
                continueMigrationsInBackground = true,
                persistMigrationAuditEvents = true,
                migrationHandler = {
                    handlerStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        handlerCancelled.complete(Unit)
                    }
                },
            ),
        )

        try {
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000.milliseconds) { handlerStarted.await() }
            }
            val modelPrefix = ownerStore.getTableDirs(1u).modelPrefix
            val stateKey = packKey(modelPrefix, modelMigrationStateKey)
            val leaseKey = packKey(modelPrefix, modelMigrationLeaseKey)
            val stateBeforeLoss = ownerStore.tc.run { transaction ->
                transaction.get(stateKey).awaitResult()
            }
            assertNotNull(stateBeforeLoss)

            ownerStore.tc.run { transaction ->
                transaction.set(
                    leaseKey,
                    "v=1\nowner=contender\nmigration=Model:1.1->2\nexpires=0\n".encodeToByteArray(),
                )
            }

            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000.milliseconds) { handlerCancelled.await() }
            }
            assertTrue(ownerStore.pendingMigrations().containsKey(1u), ownerStore.pendingMigrations().toString())
            assertFailsWith<MigrationException> {
                withContext(Dispatchers.Default.limitedParallelism(1)) {
                    withTimeout(5_000.milliseconds) { ownerStore.awaitMigration(1u) }
                }
            }

            val stateAfterLoss = ownerStore.tc.run { transaction ->
                transaction.get(stateKey).awaitResult()
            }
            assertContentEquals(stateBeforeLoss, stateAfterLoss)
            assertTrue(
                ownerStore.migrationAuditEvents(1u, limit = 20)
                    .none { it.type == MigrationAuditEventType.Completed }
            )

            FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { MigrationOutcome.Success },
                ),
            ).close()
        } finally {
            ownerStore.close()
        }
    }

    @Test
    fun ownershipReplacementFencesRealVersionHandlerWrite() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-handler-write-fence", Uuid.random().toString())
        FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1),
        ).close()

        assertFailsWith<FoundationDBMigrationLeaseLostException> {
            FoundationDBDataStore.open(
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { MigrationOutcome.Success },
                ),
                versionUpdateHandler = { store, oldModel, _ ->
                    if (oldModel != null) {
                        val leaseKey = packKey(store.getTableDirs(1u).modelPrefix, modelMigrationLeaseKey)
                        store.tc.run { transaction ->
                            transaction.set(
                                leaseKey,
                                "v=1\nowner=contender\nmigration=Model:1.1->2\nexpires=0\n".encodeToByteArray(),
                            )
                        }
                        store.execute(
                            ModelV2.add(ModelV2.create {
                                value with "handler-write"
                                newNumber with 1
                            })
                        )
                    }
                },
            )
        }

        val resumed = FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationHandler = { MigrationOutcome.Success },
            ),
        )
        assertTrue(
            resumed.execute(ModelV2.scan(order = ModelV2 { value::ref }.ascending())).values.isEmpty()
        )
        resumed.close()
    }

    @Test
    fun ownershipReplacementFencesMigrationHookWriteToAnotherModelWithoutBlockingOrdinaryWrite() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-handler-cross-model-write-fence", Uuid.random().toString())
        FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1, 2u to SimpleMarykModel),
        ).close()
        var handlerWriteKey: ByteArray? = null

        assertFailsWith<FoundationDBMigrationLeaseLostException> {
            FoundationDBDataStore.open(
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV2, 2u to SimpleMarykModel),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { MigrationOutcome.Success },
                ),
                versionUpdateHandler = { store, oldModel, _ ->
                    if (oldModel != null) {
                        val modelPrefix = store.getTableDirs(1u).modelPrefix
                        val leaseKey = packKey(modelPrefix, modelMigrationLeaseKey)
                        handlerWriteKey = packKey(store.getTableDirs(2u).modelPrefix, byteArrayOf(99))
                        store.tc.run { transaction ->
                            transaction.set(
                                leaseKey,
                                "v=1\nowner=contender\nmigration=Model:1.1->2\nexpires=0\n".encodeToByteArray(),
                            )
                        }
                        assertFailsWith<FoundationDBMigrationLeaseLostException> {
                            store.runRequestTransaction(2u) { transaction ->
                                transaction.set(
                                    requireNotNull(handlerWriteKey),
                                    byteArrayOf(1),
                                )
                            }
                        }
                    }
                },
            )
        }
        val resumed = FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2, 2u to SimpleMarykModel),
            migrationConfiguration = MigrationConfiguration(
                migrationHandler = { MigrationOutcome.Success },
            ),
        )
        try {
            assertNull(
                resumed.runTransaction(2u) { transaction ->
                    transaction.get(requireNotNull(handlerWriteKey)).awaitResult()
                },
            )
        } finally {
            resumed.close()
        }
    }

    @Test
    fun ownershipReplacementFencesRealMigrationPhaseHandlerWrite() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-phase-write-fence", Uuid.random().toString())
        var protectedKey: ByteArray? = null
        FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1),
        ).close()

        assertFailsWith<FoundationDBMigrationLeaseLostException> {
            FoundationDBDataStore.open(
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { context ->
                        val store = context.store
                        val modelPrefix = store.getTableDirs(1u).modelPrefix
                        val leaseKey = packKey(modelPrefix, modelMigrationLeaseKey)
                        protectedKey = packKey(modelPrefix, byteArrayOf(98))
                        store.tc.run { transaction ->
                            transaction.set(
                                leaseKey,
                                "v=1\nowner=contender\nmigration=Model:1.1->2.0\nexpires=0\n".encodeToByteArray(),
                            )
                        }
                        store.runTransaction(1u) { transaction ->
                            transaction.set(requireNotNull(protectedKey), byteArrayOf(1))
                        }
                        MigrationOutcome.Success
                    },
                ),
            )
        }

        val resumed = FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationHandler = { MigrationOutcome.Success },
            ),
        )
        assertNull(
            resumed.runTransaction { transaction ->
                transaction.get(requireNotNull(protectedKey)).awaitResult()
            }
        )
        resumed.close()
    }

    @Test
    fun expandAndContractHooksSupportRetryAndPartial() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-hook-phases-retry", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        var expandCalls = 0
        var contractCalls = 0
        val phases = mutableListOf<String>()

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationExpandHandler = { _ ->
                    phases += "expand"
                    expandCalls += 1
                    if (expandCalls == 1) MigrationOutcome.Retry(retryAfterMs = 1) else MigrationOutcome.Success
                },
                migrationHandler = { _ ->
                    phases += "backfill"
                    MigrationOutcome.Success
                },
                migrationVerifyHandler = { _ ->
                    phases += "verify"
                    MigrationOutcome.Success
                },
                migrationContractHandler = { _ ->
                    phases += "contract"
                    contractCalls += 1
                    if (contractCalls == 1) MigrationOutcome.Partial() else MigrationOutcome.Success
                },
            )
        ).close()

        assertEquals(listOf("expand", "expand", "backfill", "verify", "contract", "contract"), phases)
    }

    @Test
    fun testMigrationWithDependents() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-with-deps", Uuid.random().toString())
        var dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to ModelWithDependents,
            )
        )

        dataStore.close()

        dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to ModelWithDependents,
            )
        )

        dataStore.close()
    }

    @Test
    fun testMigrationWithIndex() = runTest(timeout = 5.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-index", Uuid.random().toString())
        var dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to ModelV2,
            )
        )

        val addResult = dataStore.execute(
            ModelV2.add(
                ModelV2.create {
                    value with "ha1"
                    newNumber with 100
                },
                ModelV2.create {
                    value with "ha2"
                    newNumber with 50
                },
                ModelV2.create {
                    value with "ha3"
                    newNumber with 3500
                },
                ModelV2.create {
                    value with "ha4"
                    newNumber with 1
                },
            )
        )

        assertEquals(4, addResult.statuses.size)

        val keys = mutableListOf<Key<ModelV2>>()
        var initialMaxVersion = 0uL

        for (status in addResult.statuses) {
            assertIs<AddSuccess<ModelV2>>(status).apply {
                keys.add(key)
                if (version > initialMaxVersion) initialMaxVersion = version
            }
        }

        val changeResult = dataStore.execute(
            ModelV2.change(
                keys[0].change(Change(ModelV2 { newNumber::ref } with 40)),
                keys[1].change(Change(ModelV2 { newNumber::ref } with 2000)),
                keys[2].change(Change(ModelV2 { newNumber::ref } with 500)),
                keys[3].change(Change(ModelV2 { newNumber::ref } with 990))
            )
        )

        for (status in changeResult.statuses) {
            assertIs<ChangeSuccess<ModelV2>>(status)
        }

        dataStore.close()

        dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to ModelV2ExtraIndex,
            )
        )

        val scanResponse = dataStore.execute(
            ModelV2ExtraIndex.scan(
                order = ModelV2ExtraIndex { newNumber::ref }.ascending()
            )
        )

        assertEquals(4, scanResponse.values.size)

        assertEquals(40, scanResponse.values[0].values { newNumber })
        assertEquals(500, scanResponse.values[1].values { newNumber })
        assertEquals(990, scanResponse.values[2].values { newNumber })
        assertEquals(2000, scanResponse.values[3].values { newNumber })

        val historicScanResponse = dataStore.execute(
            ModelV2ExtraIndex.scan(
                order = ModelV2ExtraIndex { newNumber::ref }.descending(),
                toVersion = ULong.MAX_VALUE
            )
        )

        assertEquals(4, historicScanResponse.values.size)

        assertEquals(2000, historicScanResponse.values[0].values { newNumber })
        assertEquals(990, historicScanResponse.values[1].values { newNumber })
        assertEquals(500, historicScanResponse.values[2].values { newNumber })
        assertEquals(40, historicScanResponse.values[3].values { newNumber })

        // Historic scan at time before changes: expect initial values ordering
        val preChangeHistoric = dataStore.execute(
            ModelV2ExtraIndex.scan(
                order = ModelV2ExtraIndex { newNumber::ref }.descending(),
                toVersion = initialMaxVersion
            )
        )

        assertEquals(4, preChangeHistoric.values.size)
        assertEquals(3500, preChangeHistoric.values[0].values { newNumber })
        assertEquals(100, preChangeHistoric.values[1].values { newNumber })
        assertEquals(50, preChangeHistoric.values[2].values { newNumber })
        assertEquals(1, preChangeHistoric.values[3].values { newNumber })

        dataStore.close()
    }

    @Test
    fun failsWhenModelIdIsReusedForDifferentModelName() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-name-mismatch", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelWithDependents)
        ).close()

        assertFailsWith<StorageException> {
            FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to SimpleMarykModel)
            )
        }
    }

    @Test
    fun backgroundMigrationBlocksRequestsUntilDone() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-background", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val releaseMigration = kotlinx.coroutines.CompletableDeferred<Unit>()
        val dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationStartupBudgetMs = -1L,
                continueMigrationsInBackground = true,
                persistMigrationAuditEvents = true,
                migrationHandler = { _ ->
                    releaseMigration.await()
                    MigrationOutcome.Success
                },
            )
        )

        repeat(50) {
            if (dataStore.pendingMigrations().containsKey(1u)) return@repeat
            delay(10.milliseconds)
        }
        assertTrue { dataStore.pendingMigrations().containsKey(1u) }
        assertEquals(MigrationRuntimeState.Running, dataStore.migrationStatus(1u).state)
        val runningStatus = dataStore.migrationStatuses()[1u]
        assertEquals(MigrationRuntimeState.Running, runningStatus?.state)
        val runningAttempt = runningStatus?.attempt
        assertTrue { runningAttempt == null || runningAttempt > 0u }

        assertFailsWith<RequestException> {
            dataStore.execute(
                ModelV2.add(
                    ModelV2.create {
                        value with "hablocked"
                        newNumber with 1
                    }
                )
            )
        }

        releaseMigration.complete(Unit)
        dataStore.awaitMigration(1u)
        assertEquals(MigrationRuntimeState.Idle, dataStore.migrationStatus(1u).state)
        assertTrue { !dataStore.migrationStatuses().containsKey(1u) }
        assertTrue { dataStore.migrationMetrics(1u).started > 0u }
        assertTrue { dataStore.migrationMetrics().containsKey(1u) }
        assertTrue { dataStore.migrationAuditEvents(1u, limit = 10).isNotEmpty() }
        dataStore.execute(
            ModelV2.add(
                ModelV2.create {
                    value with "hadone"
                    newNumber with 2
                }
            )
        )

        dataStore.close()
    }

    @Test
    fun canPauseResumeAndCancelBackgroundMigration() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-control", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        var allowSuccess = false
        val dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationStartupBudgetMs = 1L,
                continueMigrationsInBackground = true,
                migrationHandler = { _ ->
                    if (allowSuccess) {
                        MigrationOutcome.Success
                    } else {
                        MigrationOutcome.Retry(retryAfterMs = 25)
                    }
                },
            )
        )

        repeat(50) {
            if (dataStore.pendingMigrations().containsKey(1u)) return@repeat
            delay(10.milliseconds)
        }
        assertTrue { dataStore.pendingMigrations().containsKey(1u) }
        assertTrue { dataStore.pauseMigration(1u) }
        assertEquals(MigrationRuntimeState.Paused, dataStore.migrationStatus(1u).state)
        val pausedStatus = dataStore.migrationStatuses()[1u]
        assertEquals(MigrationRuntimeState.Paused, pausedStatus?.state)
        val pausedAttempt = pausedStatus?.attempt
        assertTrue { pausedAttempt == null || pausedAttempt > 0u }
        delay(50.milliseconds)
        assertTrue { dataStore.resumeMigration(1u) }
        assertEquals(MigrationRuntimeState.Running, dataStore.migrationStatus(1u).state)
        assertEquals(MigrationRuntimeState.Running, dataStore.migrationStatuses()[1u]?.state)

        allowSuccess = true
        dataStore.awaitMigration(1u)
        assertEquals(MigrationRuntimeState.Idle, dataStore.migrationStatus(1u).state)
        assertTrue { !dataStore.migrationStatuses().containsKey(1u) }
        dataStore.close()

        // New run to check cancel path
        val cancelPath = listOf("maryk", "test", "fdb-migration-control-cancel", Uuid.random().toString())
        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = cancelPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val cancelStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = cancelPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationStartupBudgetMs = 1L,
                continueMigrationsInBackground = true,
                migrationHandler = { _ -> MigrationOutcome.Retry(retryAfterMs = 25) },
            )
        )

        repeat(50) {
            if (cancelStore.pendingMigrations().containsKey(1u)) return@repeat
            delay(10.milliseconds)
        }
        assertTrue { cancelStore.pendingMigrations().containsKey(1u) }
        assertTrue { cancelStore.cancelMigration(1u, "test cancel") }
        assertEquals(MigrationRuntimeState.Canceled, cancelStore.migrationStatus(1u).state)
        assertEquals(MigrationRuntimeState.Canceled, cancelStore.migrationStatuses()[1u]?.state)
        assertFailsWith<RequestException> {
            cancelStore.execute(
                ModelV2.add(
                    ModelV2.create {
                        value with "hacanceled"
                        newNumber with 3
                    }
                )
            )
        }

        cancelStore.close()
    }

    @Test
    fun backgroundMigrationVerifyPhaseBlocksUntilVerificationDone() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-verify-background", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        var verifyAttempts = 0
        val dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationStartupBudgetMs = -1L,
                continueMigrationsInBackground = true,
                migrationHandler = { _ -> MigrationOutcome.Success },
                migrationVerifyHandler = { _ ->
                    verifyAttempts += 1
                    if (verifyAttempts >= 2) {
                        MigrationOutcome.Success
                    } else {
                        MigrationOutcome.Retry(retryAfterMs = 25)
                    }
                },
            )
        )

        repeat(50) {
            if (dataStore.pendingMigrations().containsKey(1u)) return@repeat
            delay(10.milliseconds)
        }
        assertTrue { dataStore.pendingMigrations().containsKey(1u) }
        var verifyRunningStatus = dataStore.migrationStatuses()[1u]
        repeat(50) {
            if (verifyRunningStatus?.attempt != null) return@repeat
            delay(10.milliseconds)
            verifyRunningStatus = dataStore.migrationStatuses()[1u]
        }
        assertEquals(MigrationRuntimeState.Running, verifyRunningStatus?.state)
        val verifyRunningAttempt = verifyRunningStatus?.attempt
        assertTrue { verifyRunningAttempt == null || verifyRunningAttempt > 0u }

        assertFailsWith<RequestException> {
            dataStore.execute(
                ModelV2.add(
                    ModelV2.create {
                        value with "haverify-blocked"
                        newNumber with 1
                    }
                )
            )
        }

        dataStore.awaitMigration(1u)
        assertEquals(MigrationRuntimeState.Idle, dataStore.migrationStatus(1u).state)
        assertTrue { !dataStore.migrationStatuses().containsKey(1u) }
        assertTrue { verifyAttempts >= 2 }

        dataStore.close()
    }

    @Test
    fun migrationRetryPolicyThresholdStopsRetryLoop() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-retry-policy", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        assertFailsWith<MigrationException> {
            FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationRetryPolicy = MigrationRetryPolicy(maxAttempts = 1u),
                    migrationHandler = { _ -> MigrationOutcome.Retry(retryAfterMs = 1) },
                )
            )
        }
    }

    @Test
    fun migrationOrderFollowsModelDependencies() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-dependency-order", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                2u to Phase6OrderBaseV1,
                1u to Phase6OrderDependentV1,
            )
        ).close()

        val migratedModels = mutableListOf<String>()
        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                2u to Phase6OrderBaseV2,
                1u to Phase6OrderDependentV2,
            ),
            migrationConfiguration = MigrationConfiguration(
                migrationHandler = { context ->
                    migratedModels += context.newDataModel.Meta.name
                    MigrationOutcome.Success
                },
            )
        ).close()

        assertEquals(listOf("Phase6OrderBase", "Phase6OrderDependent"), migratedModels)
    }

    @Test
    fun selfReferencingModelDoesNotWaitForItsOwnMigration() = runTest(timeout = 3.minutes) {
        val model = dataModelsForTests.getValue(1u)
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "self-referencing-startup", Uuid.random().toString()),
            dataModelsById = mapOf(1u to model),
        )
        try {
            assertTrue(store.pendingMigrations().isEmpty())
            assertTrue(store.dependencyWaitingMigrationModelIds.value.isEmpty())
            store.execute(model.scan(allowTableScan = true))
        } finally {
            store.close()
        }
    }

    @Test
    fun ordinaryOpenFinalizesAllDependenciesBeforeReturning() = runTest(timeout = 3.minutes) {
        for (allowBackground in listOf(false, true)) {
            val finalized = mutableSetOf<String>()
            val store = FoundationDBDataStore.open(
                directoryPath = listOf("maryk", "test", "synchronous-dependent-startup", Uuid.random().toString()),
                dataModelsById = dataModelsForTests,
                keepUpdateHistoryIndex = true,
                migrationConfiguration = MigrationConfiguration(continueMigrationsInBackground = allowBackground),
                versionUpdateHandler = { _, _, model -> finalized += model.Meta.name },
            )
            try {
                assertEquals(dataModelsForTests.values.map { it.Meta.name }.toSet(), finalized)
                assertTrue(store.pendingMigrations().isEmpty())
                assertTrue(store.dependencyWaitingMigrationModelIds.value.isEmpty())
                for (model in dataModelsForTests.values) {
                    store.execute(model.scan(allowTableScan = true))
                }
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun dependentMigrationWaitsForBackgroundDependencyFinalization() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-dependency-completion", Uuid.random().toString())
        FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                2u to Phase6OrderBaseV1,
                1u to Phase6OrderDependentV1,
            ),
        ).close()

        val baseStarted = CompletableDeferred<Unit>()
        val releaseBase = CompletableDeferred<Unit>()
        val dependentStarted = CompletableDeferred<Unit>()
        val opening = async(Dispatchers.Default) {
            FoundationDBDataStore.open(
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(
                    2u to Phase6OrderBaseV2,
                    1u to Phase6OrderDependentV2,
                ),
                migrationConfiguration = MigrationConfiguration(
                    migrationStartupBudgetMs = -1L,
                    continueMigrationsInBackground = true,
                    migrationHandler = { context ->
                        when (context.newDataModel.Meta.name) {
                            Phase6OrderBaseV2.Meta.name -> {
                                baseStarted.complete(Unit)
                                releaseBase.await()
                            }
                            Phase6OrderDependentV2.Meta.name -> dependentStarted.complete(Unit)
                        }
                        MigrationOutcome.Success
                    },
                ),
            )
        }

        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000.milliseconds) { baseStarted.await() }
            delay(250.milliseconds)
        }
        assertTrue(!dependentStarted.isCompleted, "dependent migration started before its dependency finalized")
        val store = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000.milliseconds) { opening.await() }
        }
        try {
            assertTrue(store.pendingMigrations().containsKey(1u))
            assertFailsWith<RequestException> {
                store.execute(Phase6OrderDependentV2.scan(allowTableScan = true))
            }
            releaseBase.complete(Unit)
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000.milliseconds) { store.awaitMigration(2u) }
                withTimeout(5_000.milliseconds) { store.awaitMigration(1u) }
            }
            assertTrue(dependentStarted.isCompleted)
        } finally {
            releaseBase.complete(Unit)
            store.close()
        }
    }

    @Test
    fun dependentNewModelStaysBlockedUntilItsVersionHookFinishes() = runTest(timeout = 3.minutes) {
        val directory = listOf("maryk", "test", "dependent-new-model-readiness", Uuid.random().toString())
        FoundationDBDataStore.open(
            directoryPath = directory,
            dataModelsById = mapOf(2u to Phase6OrderBaseV1, 3u to SimpleMarykModel),
        ).close()
        val releaseBase = CompletableDeferred<Unit>()
        val hookStarted = CompletableDeferred<Unit>()
        val releaseHook = CompletableDeferred<Unit>()
        val store = FoundationDBDataStore.open(
            directoryPath = directory,
            dataModelsById = mapOf(2u to Phase6OrderBaseV2, 1u to Phase6OrderDependentV2, 3u to SimpleMarykModel),
            migrationConfiguration = MigrationConfiguration(
                migrationStartupBudgetMs = -1L,
                continueMigrationsInBackground = true,
                migrationHandler = {
                    releaseBase.await()
                    MigrationOutcome.Success
                },
            ),
            versionUpdateHandler = { currentStore, _, model ->
                if (model.Meta.name == Phase6OrderDependentV2.Meta.name) {
                    // The hook can use its own model; other callers must still be fenced.
                    currentStore.execute(Phase6OrderDependentV2.scan(allowTableScan = true))
                    hookStarted.complete(Unit)
                    releaseHook.await()
                }
            },
        )
        try {
            store.execute(SimpleMarykModel.scan(allowTableScan = true))
            releaseBase.complete(Unit)
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000.milliseconds) { hookStarted.await() }
            }
            assertFailsWith<RequestException> {
                store.execute(Phase6OrderDependentV2.scan(allowTableScan = true))
            }
            releaseHook.complete(Unit)
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000.milliseconds) { store.awaitMigration(1u) }
            }
            store.execute(Phase6OrderDependentV2.scan(allowTableScan = true))
        } finally {
            releaseBase.complete(Unit)
            releaseHook.complete(Unit)
            store.close()
        }
    }

    @Test
    fun staleCompletedMigrationStateIsClearedBeforeTheNextUpgrade() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-stale-completed-migration-state", Uuid.random().toString())
        val v2Store = FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to CrashRecoveryModelV2),
        )
        val stateKey = packKey(v2Store.getTableDirs(1u).modelPrefix, modelMigrationStateKey)
        v2Store.runTransaction { transaction ->
            transaction.set(
                stateKey,
                MigrationState(
                    migrationId = "CrashRecoveryModel:1.0->${CrashRecoveryModelV2.Meta.version}",
                    phase = MigrationPhase.Contract,
                    status = MigrationStateStatus.Running,
                    attempt = 4u,
                    fromVersion = "1.0",
                    toVersion = CrashRecoveryModelV2.Meta.version.toString(),
                    message = "Migration phases complete; finalization pending",
                ).toPersistedBytes(),
            )
        }
        v2Store.close()

        val recoveredV2Store = FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to CrashRecoveryModelV2),
        )
        try {
            assertNull(recoveredV2Store.runTransaction { transaction -> transaction.get(stateKey).awaitResult() })
        } finally {
            recoveredV2Store.close()
        }

        FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to CrashRecoveryModelV3),
            migrationConfiguration = MigrationConfiguration(
                migrationHandler = { MigrationOutcome.Success },
            ),
        ).close()
    }

    @Test
    fun staleV2CleanupDoesNotClearConcurrentV3MigrationState() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-stale-v2-v3-interleaving", Uuid.random().toString())
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to CrashRecoveryModelV2),
        )
        val modelPrefix = store.getTableDirs(1u).modelPrefix
        val stateKey = packKey(modelPrefix, modelMigrationStateKey)
        val versionKey = packKey(modelPrefix, modelVersionKey)
        val v2State = MigrationState(
            migrationId = "CrashRecoveryModel:1.0->2.0",
            phase = MigrationPhase.Contract,
            status = MigrationStateStatus.Running,
            attempt = 1u,
            fromVersion = "1.0",
            toVersion = CrashRecoveryModelV2.Meta.version.toString(),
            message = "Migration phases complete; finalization pending",
        )
        val v3State = MigrationState(
            migrationId = "CrashRecoveryModel:2.0->3.0",
            phase = MigrationPhase.Backfill,
            status = MigrationStateStatus.Running,
            attempt = 2u,
            fromVersion = CrashRecoveryModelV2.Meta.version.toString(),
            toVersion = CrashRecoveryModelV3.Meta.version.toString(),
        )
        store.tc.run { transaction -> transaction.set(stateKey, v2State.toPersistedBytes()) }

        var injectV3 = true
        val racingContext = object : TransactionContext by store.tc {
            override fun <T> run(block: (Transaction) -> T): T = store.tc.run { transaction ->
                block(transaction).also {
                    if (injectV3) {
                        injectV3 = false
                        store.tc.run { concurrent ->
                            concurrent.set(versionKey, CrashRecoveryModelV3.Meta.version.toByteArray())
                            concurrent.set(stateKey, v3State.toPersistedBytes())
                        }
                    }
                }
            }
        }
        val stateStore = FoundationDBMigrationStateStore(racingContext, mapOf(1u to modelPrefix))

        try {
            stateStore.clearFinalizedForPublishedVersion(1u, CrashRecoveryModelV2.Meta.version)
            val persisted = store.tc.run { transaction -> transaction.get(stateKey).awaitResult() }
            assertEquals(v3State, MigrationState.requireFromPersistedBytes(requireNotNull(persisted)))
        } finally {
            store.close()
        }
    }

    @Test
    fun reopensStoredModelWithReferenceToLaterSortedModel() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-reference-later-model", Uuid.random().toString())
        val models = mapOf(
            1u to Phase6ReferenceOwnerModel,
            2u to Phase6ReferenceTargetModel,
        )

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = models,
        ).close()

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = models,
        ).close()
    }

    @Test
    fun migrationCycleInModelsIsRejected() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-dependency-cycle", Uuid.random().toString())

        val exception = assertFailsWith<MigrationException> {
            FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(
                    1u to Phase6CycleLeftModel,
                    2u to Phase6CycleRightModel,
                )
            )
        }

        assertTrue(exception.message.orEmpty().contains("Dependency cycle detected"))
    }

    @Test
    fun leaseIsReleasedWhenStartupMigrationFails() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-lease-release-failure", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val lease = ScriptedMigrationLease(
            mutableMapOf(1u to ArrayDeque(listOf(true)))
        )

        assertFailsWith<MigrationException> {
            FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationLease = lease,
                    migrationHandler = { _ -> MigrationOutcome.Fatal("boom") },
                )
            )
        }

        assertEquals(1, lease.releaseCalls.value)
    }

    @Test
    fun backgroundMigrationRetriesLeaseAcquisitionAndCompletes() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-lease-retry-background", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val lease = ScriptedMigrationLease(
            mutableMapOf(1u to ArrayDeque(listOf(false, false, true)))
        )
        val dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                continueMigrationsInBackground = true,
                migrationLease = lease,
                migrationHandler = { _ -> MigrationOutcome.Success },
            )
        )

        try {
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000.milliseconds) {
                    dataStore.awaitMigration(1u)
                }
            }
            assertEquals(MigrationRuntimeState.Idle, dataStore.migrationStatus(1u).state)
            assertTrue(lease.tryAcquireCalls.value >= 3)
            assertEquals(1, lease.releaseCalls.value)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun backgroundMigrationReportsLeaseReleaseFailureToWaiters() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-release-after-success", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val lease = ScriptedMigrationLease(
            mutableMapOf(1u to ArrayDeque(listOf(false, true))),
            throwOnRelease = true,
        )
        val dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                continueMigrationsInBackground = true,
                migrationLease = lease,
                migrationHandler = { MigrationOutcome.Success },
            )
        )

        try {
            val exception = withContext(Dispatchers.Default.limitedParallelism(1)) {
                assertFailsWith<MigrationException> {
                    withTimeout(5_000.milliseconds) {
                        dataStore.awaitMigration(1u)
                    }
                }
            }
            assertTrue(exception.message.orEmpty().contains("lease release failed"))
            assertEquals(1, lease.releaseCalls.value)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun startupFailureReleasesEarlierDeferredMigrationLease() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-deferred-migration-release", Uuid.random().toString())
        FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to DeferredFinalizerModelV1,
                2u to FailingStartupModelV1,
            ),
        ).close()

        val lease = ScriptedMigrationLease(
            mutableMapOf(
                1u to ArrayDeque(listOf(true)),
                2u to ArrayDeque(listOf(true)),
            ),
        )
        assertFailsWith<MigrationException> {
            FoundationDBDataStore.open(
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(
                    1u to DeferredFinalizerModelV2,
                    2u to FailingStartupModelV2,
                ),
                migrationConfiguration = MigrationConfiguration(
                    migrationLease = lease,
                    migrationHandler = { context ->
                        if (context.newDataModel.Meta.name == FailingStartupModelV2.Meta.name) {
                            MigrationOutcome.Fatal("later migration failed")
                        } else {
                            MigrationOutcome.Success
                        }
                    },
                ),
            )
        }

        assertEquals(2, lease.releaseCalls.value)
    }

    @Test
    fun failedBackgroundAuditAfterAcquisitionDoesNotAbandonRenewingLease() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-audit-acquire-failure", Uuid.random().toString())
        val controlStore = FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1),
        )
        val modelPrefix = controlStore.getTableDirs(1u).modelPrefix
        val migrationId = "Model:1.1->2"
        val blockerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val contenderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val blocker = FoundationDBMigrationLease(
            controlStore.tc,
            mapOf(1u to modelPrefix),
            blockerScope,
            leaseTimeoutMs = 400,
            heartbeatIntervalMs = 50,
        )
        val contender = FoundationDBMigrationLease(
            controlStore.tc,
            mapOf(1u to modelPrefix),
            contenderScope,
            leaseTimeoutMs = 400,
            heartbeatIntervalMs = 50,
        )
        assertTrue(blocker.tryAcquire(1u, migrationId))

        val migratingStore = FoundationDBDataStore.open(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationLeaseConfiguration = FoundationDBMigrationLeaseConfiguration(
                migrationLeaseTimeoutMs = 400,
                migrationLeaseHeartbeatMs = 50,
            ),
            migrationConfiguration = MigrationConfiguration(
                continueMigrationsInBackground = true,
                persistMigrationAuditEvents = true,
                migrationHandler = { MigrationOutcome.Success },
            ),
        )

        try {
            val auditKey = packKey(modelPrefix, modelMigrationAuditLogKey)
            controlStore.runTransaction { transaction ->
                transaction.set(
                    auditKey,
                    byteArrayOf(0, 0x4d, 0x41, 0x55, 1, 0, 0, 0, 0, 0, 0, 0, 0),
                )
            }
            blocker.release(1u, migrationId)

            withContext(Dispatchers.Default.limitedParallelism(1)) {
                assertFailsWith<MigrationException> {
                    withTimeout(5_000.milliseconds) { migratingStore.awaitMigration(1u) }
                }
                withTimeout(5_000.milliseconds) {
                    while (!contender.tryAcquire(1u, migrationId)) {
                        delay(50.milliseconds)
                    }
                }
            }
        } finally {
            contender.release(1u, migrationId)
            blocker.release(1u, migrationId)
            migratingStore.close()
            controlStore.close()
            blockerScope.cancel()
            contenderScope.cancel()
        }
    }

    @Test
    fun waitingMigratorDoesNotRunStalePlanAfterAnotherStoreCompletes() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-stale-plan-handoff", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val allowAcquire = atomic(false)
        val delayedLease = GatedMigrationLease { allowAcquire.value }
        val firstHandlerCalls = atomic(0)

        val waitingStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                continueMigrationsInBackground = true,
                migrationLease = delayedLease,
                migrationHandler = { _ ->
                    firstHandlerCalls.incrementAndGet()
                    MigrationOutcome.Fatal("stale plan should not run")
                },
            )
        )

        try {
            repeat(50) {
                if (waitingStore.pendingMigrations().containsKey(1u)) return@repeat
                delay(10.milliseconds)
            }
            assertTrue { waitingStore.pendingMigrations().containsKey(1u) }

            FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { _ -> MigrationOutcome.Success },
                )
            ).close()

            allowAcquire.value = true

            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000.milliseconds) {
                    waitingStore.awaitMigration(1u)
                }
            }

            assertEquals(0, firstHandlerCalls.value)
            assertEquals(MigrationRuntimeState.Idle, waitingStore.migrationStatus(1u).state)
            waitingStore.execute(
                ModelV2.add(
                    ModelV2.create {
                        value with "hapost-handoff"
                        newNumber with 6
                    }
                )
            )
        } finally {
            waitingStore.close()
        }
    }

    @Test
    fun reopensAndResumesEachPhaseFromRetryState() = runTest(timeout = 3.minutes) {
        for (targetPhase in MigrationPhase.entries) {
            val dirPath = listOf("maryk", "test", "fdb-migration-resume-${targetPhase.name.lowercase()}", Uuid.random().toString())
            FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV1_1)
            ).close()

            var retryIssued = false
            val firstStore = FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationLease = NoopMigrationLease,
                    migrationStartupBudgetMs = -1L,
                    continueMigrationsInBackground = true,
                    migrationExpandHandler = {
                        if (targetPhase == MigrationPhase.Expand && !retryIssued) {
                            retryIssued = true
                            MigrationOutcome.Retry(
                                nextCursor = byteArrayOf(targetPhase.ordinal.toByte()),
                                retryAfterMs = 5_000
                            )
                        } else {
                            MigrationOutcome.Success
                        }
                    },
                    migrationHandler = {
                        if (targetPhase == MigrationPhase.Backfill && !retryIssued) {
                            retryIssued = true
                            MigrationOutcome.Retry(
                                nextCursor = byteArrayOf(targetPhase.ordinal.toByte()),
                                retryAfterMs = 5_000
                            )
                        } else {
                            MigrationOutcome.Success
                        }
                    },
                    migrationVerifyHandler = {
                        if (targetPhase == MigrationPhase.Verify && !retryIssued) {
                            retryIssued = true
                            MigrationOutcome.Retry(
                                nextCursor = byteArrayOf(targetPhase.ordinal.toByte()),
                                retryAfterMs = 5_000
                            )
                        } else {
                            MigrationOutcome.Success
                        }
                    },
                    migrationContractHandler = {
                        if (targetPhase == MigrationPhase.Contract && !retryIssued) {
                            retryIssued = true
                            MigrationOutcome.Retry(
                                nextCursor = byteArrayOf(targetPhase.ordinal.toByte()),
                                retryAfterMs = 5_000
                            )
                        } else {
                            MigrationOutcome.Success
                        }
                    },
                )
            )

            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000.milliseconds) {
                    while (true) {
                        val status = firstStore.migrationStatus(1u)
                        if (status.phase == targetPhase && status.hasCursor == true) break
                        delay(10.milliseconds)
                    }
                }
            }
            val pendingStatus = firstStore.migrationStatus(1u)
            assertEquals(targetPhase, pendingStatus.phase)
            assertEquals(true, pendingStatus.hasCursor)
            firstStore.close()

            var resumed = false
            val secondStore = FoundationDBDataStore.open(
                keepAllVersions = true,
                fdbClusterFilePath = "fdb.cluster",
                directoryPath = dirPath,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationLease = NoopMigrationLease,
                    migrationExpandHandler = { context ->
                        if (targetPhase == MigrationPhase.Expand) {
                            val previousState = context.previousState
                            assertNotNull(previousState)
                            assertEquals(MigrationPhase.Expand, previousState.phase)
                            assertEquals(MigrationStateStatus.Retry, previousState.status)
                            assertContentEquals(byteArrayOf(targetPhase.ordinal.toByte()), previousState.cursor)
                            resumed = true
                        }
                        MigrationOutcome.Success
                    },
                    migrationHandler = { context ->
                        if (targetPhase == MigrationPhase.Backfill) {
                            val previousState = context.previousState
                            assertNotNull(previousState)
                            assertEquals(MigrationPhase.Backfill, previousState.phase)
                            assertEquals(MigrationStateStatus.Retry, previousState.status)
                            assertContentEquals(byteArrayOf(targetPhase.ordinal.toByte()), previousState.cursor)
                            resumed = true
                        }
                        MigrationOutcome.Success
                    },
                    migrationVerifyHandler = { context ->
                        if (targetPhase == MigrationPhase.Verify) {
                            val previousState = context.previousState
                            assertNotNull(previousState)
                            assertEquals(MigrationPhase.Verify, previousState.phase)
                            assertEquals(MigrationStateStatus.Retry, previousState.status)
                            assertContentEquals(byteArrayOf(targetPhase.ordinal.toByte()), previousState.cursor)
                            resumed = true
                        }
                        MigrationOutcome.Success
                    },
                    migrationContractHandler = { context ->
                        if (targetPhase == MigrationPhase.Contract) {
                            val previousState = context.previousState
                            assertNotNull(previousState)
                            assertEquals(MigrationPhase.Contract, previousState.phase)
                            assertEquals(MigrationStateStatus.Retry, previousState.status)
                            assertContentEquals(byteArrayOf(targetPhase.ordinal.toByte()), previousState.cursor)
                            resumed = true
                        }
                        MigrationOutcome.Success
                    },
                )
            )

            assertTrue(resumed)
            secondStore.close()
        }
    }

    @Test
    fun customLeaseWorksWithAllPhaseHooks() = runTest(timeout = 3.minutes) {
        val dirPath = listOf("maryk", "test", "fdb-migration-lease-all-phases", Uuid.random().toString())

        FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV1_1),
        ).close()

        val lease = ScriptedMigrationLease(
            mutableMapOf(1u to ArrayDeque(listOf(false, false, true)))
        )
        val phases = mutableListOf<String>()
        val dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                continueMigrationsInBackground = true,
                migrationLease = lease,
                migrationExpandHandler = { _ ->
                    phases += "expand"
                    MigrationOutcome.Success
                },
                migrationHandler = { _ ->
                    phases += "backfill"
                    MigrationOutcome.Success
                },
                migrationVerifyHandler = { _ ->
                    phases += "verify"
                    MigrationOutcome.Success
                },
                migrationContractHandler = { _ ->
                    phases += "contract"
                    MigrationOutcome.Success
                },
            )
        )

        try {
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000.milliseconds) {
                    dataStore.awaitMigration(1u)
                }
            }
            assertEquals(listOf("expand", "backfill", "verify", "contract"), phases)
            assertTrue(lease.tryAcquireCalls.value >= 3)
        } finally {
            dataStore.close()
        }
    }
}

private class ScriptedMigrationLease(
    private val outcomesByModelId: MutableMap<UInt, ArrayDeque<Boolean>> = mutableMapOf(),
    private val throwOnRelease: Boolean = false,
) : MigrationLease {
    val tryAcquireCalls = atomic(0)
    val releaseCalls = atomic(0)

    override suspend fun tryAcquire(modelId: UInt, migrationId: String): Boolean {
        tryAcquireCalls.incrementAndGet()
        return outcomesByModelId[modelId]?.removeFirstOrNull() ?: true
    }

    override suspend fun release(modelId: UInt, migrationId: String) {
        releaseCalls.incrementAndGet()
        if (throwOnRelease) error("release failed")
    }
}

private class GatedMigrationLease(
    private val canAcquire: () -> Boolean,
) : MigrationLease {
    override suspend fun tryAcquire(modelId: UInt, migrationId: String): Boolean = canAcquire()

    override suspend fun release(modelId: UInt, migrationId: String) = Unit
}

private class TrackingMigrationLease : MigrationLease {
    val releaseCalls = atomic(0)
    var isHeld = false
        private set

    override suspend fun tryAcquire(modelId: UInt, migrationId: String): Boolean {
        isHeld = true
        return true
    }

    override suspend fun release(modelId: UInt, migrationId: String) {
        isHeld = false
        releaseCalls.incrementAndGet()
    }
}

private object DeferredFinalizerModelV1 : RootDataModel<DeferredFinalizerModelV1>(
    name = "DeferredFinalizerModel",
    version = Version(1),
) {
    val value by string(index = 1u)
}

private object DeferredFinalizerModelV2 : RootDataModel<DeferredFinalizerModelV2>(
    name = "DeferredFinalizerModel",
    version = Version(2),
) {
    val value by string(index = 1u)
    val added by string(index = 2u, required = false)
}

private object FailingStartupModelV1 : RootDataModel<FailingStartupModelV1>(
    name = "FailingStartupModel",
    version = Version(1),
) {
    val value by string(index = 1u)
}

private object FailingStartupModelV2 : RootDataModel<FailingStartupModelV2>(
    name = "FailingStartupModel",
    version = Version(2),
) {
    val value by string(index = 1u)
    val added by string(index = 2u, required = false)
}

private object Phase6OrderBaseV1 : RootDataModel<Phase6OrderBaseV1>(
    name = "Phase6OrderBase",
    version = Version(1),
) {
    val value by string(index = 1u)
}

private object Phase6OrderBaseV2 : RootDataModel<Phase6OrderBaseV2>(
    name = "Phase6OrderBase",
    version = Version(2),
) {
    val value by number(index = 1u, type = SInt32, required = true)
}

private object Phase6OrderDependentV1 : RootDataModel<Phase6OrderDependentV1>(
    name = "Phase6OrderDependent",
    version = Version(1),
) {
    val base by embed(index = 1u, required = false, dataModel = { Phase6OrderBaseV1 })
    val value by string(index = 2u)
}

private object Phase6OrderDependentV2 : RootDataModel<Phase6OrderDependentV2>(
    name = "Phase6OrderDependent",
    version = Version(2),
) {
    val base by embed(index = 1u, required = false, dataModel = { Phase6OrderBaseV2 })
    val value by number(index = 2u, type = SInt32, required = true)
}

private object Phase6CycleLeftModel : RootDataModel<Phase6CycleLeftModel>(
    name = "Phase6CycleLeftModel",
    version = Version(1),
) {
    val right by embed(index = 1u, required = false, dataModel = { Phase6CycleRightModel })
}

private object Phase6CycleRightModel : RootDataModel<Phase6CycleRightModel>(
    name = "Phase6CycleRightModel",
    version = Version(1),
) {
    val left by embed(index = 1u, required = false, dataModel = { Phase6CycleLeftModel })
}

private object Phase6ReferenceOwnerModel : RootDataModel<Phase6ReferenceOwnerModel>(
    name = "Phase6ReferenceOwner",
    version = Version(1),
) {
    val target by reference(index = 1u, required = false, dataModel = { Phase6ReferenceTargetModel })
}

private object Phase6ReferenceTargetModel : RootDataModel<Phase6ReferenceTargetModel>(
    name = "Phase6ReferenceTarget",
    version = Version(1),
) {
    val value by string(index = 1u)
}

private object CrashRecoveryModelV2 : RootDataModel<CrashRecoveryModelV2>(
    name = "CrashRecoveryModel",
    version = Version(2),
) {
    val value by number(index = 1u, type = SInt32, required = true)
}

private object CrashRecoveryModelV3 : RootDataModel<CrashRecoveryModelV3>(
    name = "CrashRecoveryModel",
    version = Version(3),
) {
    val value by string(index = 1u, required = true)
}
