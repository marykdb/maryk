@file:Suppress("unused")

package maryk.datastore.rocksdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationLease
import maryk.core.models.migration.MigrationOutcome
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationRetryPolicy
import maryk.core.models.migration.MigrationRuntimeState
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.models.migration.NoopMigrationLease
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
import maryk.createTestDBFolder
import maryk.datastore.test.dataModelsForTests
import maryk.deleteFolder
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

class RocksDBDataStoreMigrationTest {

    class CustomException : Error()

    @Test
    fun testComplexMigrationCheckWithNoChange() = runTest {
        val path = createTestDBFolder("migrationWithDepsNoChange")
        var dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = dataModelsForTests
        )

        dataStore.close()

        dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = dataModelsForTests
        )

        dataStore.close()

        deleteFolder(path)
    }

    @Test
    fun testMigration() = runTest {
        val path = createTestDBFolder("migration")
        var didRunUpdateHandler = false

        var dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
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

        dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
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
            RocksDBDataStore.open(
                keepAllVersions = true,
                relativePath = path,
                dataModelsById = mapOf(
                    1u to ModelV2,
                ),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = null,
                )
            )
        }

        assertFailsWith<CustomException> {
            RocksDBDataStore.open(
                keepAllVersions = true,
                relativePath = path,
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

        deleteFolder(path)
    }

    @Test
    fun migrationRunsAllPhaseHooksInOrder() = runTest {
        val path = createTestDBFolder("migrationHookPhases")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val phases = mutableListOf<String>()
        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
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

        deleteFolder(path)
    }

    @Test
    fun expandAndContractHooksSupportRetryAndPartial() = runTest {
        val path = createTestDBFolder("migrationHookPhasesRetry")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        var expandCalls = 0
        var contractCalls = 0
        val phases = mutableListOf<String>()

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
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

        deleteFolder(path)
    }

    @Test
    fun testMigrationWithDependents() = runTest {
        val path = createTestDBFolder("migrationWithDeps")
        var dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(
                1u to ModelWithDependents,
            )
        )

        dataStore.close()

        dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(
                1u to ModelWithDependents,
            )
        )

        dataStore.close()

        deleteFolder(path)
    }

    @Test
    fun testMigrationWithIndex() = runTest {
        val path = createTestDBFolder("migration2")
        var dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(
                1u to ModelV2,
            )
        )

        val addResult = dataStore.execute(
            ModelV2.add(
                ModelV2.create { value with "ha1"; newNumber with 100 },
                ModelV2.create { value with "ha2"; newNumber with 50 },
                ModelV2.create { value with "ha3"; newNumber with 3500 },
                ModelV2.create { value with "ha4"; newNumber with 1 },
            )
        )

        assertEquals(4, addResult.statuses.size)

        val keys = mutableListOf<Key<ModelV2>>()

        for (status in addResult.statuses) {
            assertIs<AddSuccess<ModelV2>>(status).apply {
                keys.add(key)
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

        dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
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

        dataStore.close()

        deleteFolder(path)
    }

    @Test
    fun failsWhenModelIdIsReusedForDifferentModelName() = runTest {
        val path = createTestDBFolder("migrationNameMismatch")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelWithDependents)
        ).close()

        assertFailsWith<StorageException> {
            RocksDBDataStore.open(
                keepAllVersions = true,
                relativePath = path,
                dataModelsById = mapOf(1u to SimpleMarykModel)
            )
        }

        deleteFolder(path)
    }

    @Test
    fun backgroundMigrationBlocksRequestsUntilDone() = runTest {
        val path = createTestDBFolder("migrationBackground")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val releaseMigration = kotlinx.coroutines.CompletableDeferred<Unit>()
        val dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
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
            delay(10)
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
        deleteFolder(path)
    }

    @Test
    fun canPauseResumeAndCancelBackgroundMigration() = runTest {
        val path = createTestDBFolder("migrationControl")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        var allowSuccess = false
        val dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
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
            delay(10)
        }
        assertTrue { dataStore.pendingMigrations().containsKey(1u) }
        assertTrue { dataStore.pauseMigration(1u) }
        assertEquals(MigrationRuntimeState.Paused, dataStore.migrationStatus(1u).state)
        val pausedStatus = dataStore.migrationStatuses()[1u]
        assertEquals(MigrationRuntimeState.Paused, pausedStatus?.state)
        val pausedAttempt = pausedStatus?.attempt
        assertTrue { pausedAttempt == null || pausedAttempt > 0u }
        delay(50)
        assertTrue { dataStore.resumeMigration(1u) }
        assertEquals(MigrationRuntimeState.Running, dataStore.migrationStatus(1u).state)
        assertEquals(MigrationRuntimeState.Running, dataStore.migrationStatuses()[1u]?.state)

        allowSuccess = true
        dataStore.awaitMigration(1u)
        assertEquals(MigrationRuntimeState.Idle, dataStore.migrationStatus(1u).state)
        assertTrue { !dataStore.migrationStatuses().containsKey(1u) }

        // New run to check cancel path
        dataStore.close()

        val cancelPath = createTestDBFolder("migrationControlCancel")
        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = cancelPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val cancelStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = cancelPath,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationStartupBudgetMs = 1L,
                continueMigrationsInBackground = true,
                migrationHandler = { _ -> MigrationOutcome.Retry(retryAfterMs = 25) },
            )
        )

        repeat(50) {
            if (cancelStore.pendingMigrations().containsKey(1u)) return@repeat
            delay(10)
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
        deleteFolder(cancelPath)
        deleteFolder(path)
    }

    @Test
    fun backgroundMigrationVerifyPhaseBlocksUntilVerificationDone() = runTest {
        val path = createTestDBFolder("migrationVerifyBackground")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        var verifyAttempts = 0
        val dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
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
            delay(10)
        }
        assertTrue { dataStore.pendingMigrations().containsKey(1u) }
        var verifyRunningStatus = dataStore.migrationStatuses()[1u]
        repeat(50) {
            if (verifyRunningStatus?.attempt != null) return@repeat
            delay(10)
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
        deleteFolder(path)
    }

    @Test
    fun migrationRetryPolicyThresholdStopsRetryLoop() = runTest {
        val path = createTestDBFolder("migrationRetryPolicy")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        assertFailsWith<MigrationException> {
            RocksDBDataStore.open(
                keepAllVersions = true,
                relativePath = path,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationRetryPolicy = MigrationRetryPolicy(maxAttempts = 1u),
                    migrationHandler = { _ -> MigrationOutcome.Retry(retryAfterMs = 1) },
                )
            )
        }

        deleteFolder(path)
    }

    @Test
    fun migrationOrderFollowsModelDependencies() = runTest {
        val path = createTestDBFolder("migrationDependencyOrder")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(
                2u to Phase6OrderBaseV1,
                1u to Phase6OrderDependentV1,
            )
        ).close()

        val migratedModels = mutableListOf<String>()
        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
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
        deleteFolder(path)
    }

    @Test
    fun migrationCycleInModelsIsRejected() = runTest {
        val path = createTestDBFolder("migrationDependencyCycle")

        val exception = assertFailsWith<MigrationException> {
            RocksDBDataStore.open(
                keepAllVersions = true,
                relativePath = path,
                dataModelsById = mapOf(
                    1u to Phase6CycleLeftModel,
                    2u to Phase6CycleRightModel,
                )
            )
        }

        assertTrue(exception.message.orEmpty().contains("Dependency cycle detected"))
        deleteFolder(path)
    }

    @Test
    fun leaseIsReleasedWhenStartupMigrationFails() = runTest {
        val path = createTestDBFolder("migrationLeaseReleaseOnFailure")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val lease = ScriptedMigrationLease(
            mutableMapOf(1u to ArrayDeque(listOf(true)))
        )

        assertFailsWith<MigrationException> {
            RocksDBDataStore.open(
                keepAllVersions = true,
                relativePath = path,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationLease = lease,
                    migrationHandler = { _ -> MigrationOutcome.Fatal("boom") },
                )
            )
        }

        assertEquals(1, lease.releaseCalls.value)
        deleteFolder(path)
    }

    @Test
    fun backgroundMigrationRetriesLeaseAcquisitionAndCompletes() = runTest {
        val path = createTestDBFolder("migrationLeaseRetryBackground")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val lease = ScriptedMigrationLease(
            mutableMapOf(1u to ArrayDeque(listOf(false, false, true)))
        )
        val dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                continueMigrationsInBackground = true,
                migrationLease = lease,
                migrationHandler = { _ -> MigrationOutcome.Success },
            )
        )

        try {
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000) {
                    dataStore.awaitMigration(1u)
                }
            }

            assertEquals(MigrationRuntimeState.Idle, dataStore.migrationStatus(1u).state)
            assertTrue(lease.tryAcquireCalls.value >= 3)
            assertEquals(1, lease.releaseCalls.value)
        } finally {
            dataStore.close()
            deleteFolder(path)
        }
    }

    @Test
    fun canceledMigrationCanResumeAfterReopen() = runTest {
        val path = createTestDBFolder("migrationCancelResumeReopen")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val canceledStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationStartupBudgetMs = 1L,
                continueMigrationsInBackground = true,
                migrationHandler = { _ -> MigrationOutcome.Retry(retryAfterMs = 25) },
            )
        )

        repeat(50) {
            if (canceledStore.pendingMigrations().containsKey(1u)) return@repeat
            delay(10)
        }

        assertTrue { canceledStore.pendingMigrations().containsKey(1u) }
        assertTrue { canceledStore.cancelMigration(1u, "resume on reopen") }
        assertEquals(MigrationRuntimeState.Canceled, canceledStore.migrationStatus(1u).state)
        assertFailsWith<RequestException> {
            canceledStore.execute(
                ModelV2.add(
                    ModelV2.create {
                        value with "hastill-blocked"
                        newNumber with 4
                    }
                )
            )
        }
        canceledStore.close()

        val resumedStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationHandler = { _ -> MigrationOutcome.Success },
            )
        )

        try {
            resumedStore.execute(
                ModelV2.add(
                    ModelV2.create {
                        value with "haresumed"
                        newNumber with 5
                    }
                )
            )
        } finally {
            resumedStore.close()
            deleteFolder(path)
        }
    }

    @Test
    fun omittedOptionalPhaseHooksDefaultToSuccess() = runTest {
        val path = createTestDBFolder("migrationOmittedOptionalHooks")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        var backfillCalls = 0
        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                migrationHandler = { _ ->
                    backfillCalls += 1
                    MigrationOutcome.Success
                },
            )
        ).close()

        assertEquals(1, backfillCalls)
        deleteFolder(path)
    }

    @Test
    fun expandAndContractFatalFailMigration() = runTest {
        val expandPath = createTestDBFolder("migrationExpandFatal")
        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = expandPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val expandException = assertFailsWith<MigrationException> {
            RocksDBDataStore.open(
                keepAllVersions = true,
                relativePath = expandPath,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationExpandHandler = { _ -> MigrationOutcome.Fatal("expand failed") },
                    migrationHandler = { _ -> MigrationOutcome.Success },
                )
            )
        }
        assertTrue(expandException.message.orEmpty().contains("Expand"))
        deleteFolder(expandPath)

        val contractPath = createTestDBFolder("migrationContractFatal")
        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = contractPath,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        val contractException = assertFailsWith<MigrationException> {
            RocksDBDataStore.open(
                keepAllVersions = true,
                relativePath = contractPath,
                dataModelsById = mapOf(1u to ModelV2),
                migrationConfiguration = MigrationConfiguration(
                    migrationHandler = { _ -> MigrationOutcome.Success },
                    migrationVerifyHandler = { _ -> MigrationOutcome.Success },
                    migrationContractHandler = { _ -> MigrationOutcome.Fatal("contract failed") },
                )
            )
        }
        assertTrue(contractException.message.orEmpty().contains("Contract"))
        deleteFolder(contractPath)
    }

    @Test
    fun reopensAndResumesEachPhaseFromRetryState() = runTest {
        for (targetPhase in MigrationPhase.entries) {
            val path = createTestDBFolder("migrationResume${targetPhase.name}")
            RocksDBDataStore.open(
                keepAllVersions = true,
                relativePath = path,
                dataModelsById = mapOf(1u to ModelV1_1)
            ).close()

            var retryIssued = false
            val firstStore = RocksDBDataStore.open(
                keepAllVersions = true,
                relativePath = path,
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
                withTimeout(5_000) {
                    while (true) {
                        val status = firstStore.migrationStatus(1u)
                        if (status.phase == targetPhase && status.hasCursor == true) break
                        delay(10)
                    }
                }
            }
            val pendingStatus = firstStore.migrationStatus(1u)
            assertEquals(targetPhase, pendingStatus.phase)
            assertEquals(true, pendingStatus.hasCursor)
            firstStore.close()

            var resumed = false
            val secondStore = RocksDBDataStore.open(
                keepAllVersions = true,
                relativePath = path,
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
            deleteFolder(path)
        }
    }

    @Test
    fun auditLogAndMetricsTrackPhaseOutcomes() = runTest {
        val path = createTestDBFolder("migrationAuditMetrics")

        RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV1_1)
        ).close()

        var expandCalls = 0
        var verifyCalls = 0
        val dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = path,
            dataModelsById = mapOf(1u to ModelV2),
            migrationConfiguration = MigrationConfiguration(
                persistMigrationAuditEvents = true,
                migrationExpandHandler = { _ ->
                    expandCalls += 1
                    if (expandCalls == 1) MigrationOutcome.Retry(retryAfterMs = 1) else MigrationOutcome.Success
                },
                migrationHandler = { _ -> MigrationOutcome.Success },
                migrationVerifyHandler = { _ ->
                    verifyCalls += 1
                    if (verifyCalls == 1) MigrationOutcome.Partial(nextCursor = byteArrayOf(9)) else MigrationOutcome.Success
                },
                migrationContractHandler = { _ -> MigrationOutcome.Success },
            )
        )

        try {
            val metrics = dataStore.migrationMetrics(1u)
            assertEquals(6u, metrics.started)
            assertEquals(1u, metrics.retries)
            assertEquals(1u, metrics.partials)
            assertEquals(1u, metrics.completed)

            val events = dataStore.migrationAuditEvents(1u, limit = 20)
            assertTrue(events.any { it.type.name == "RetryScheduled" && it.phase == MigrationPhase.Expand })
            assertTrue(events.any { it.type.name == "Partial" && it.phase == MigrationPhase.Verify })
            assertTrue(events.any { it.type.name == "Completed" && it.phase == MigrationPhase.Contract })
        } finally {
            dataStore.close()
            deleteFolder(path)
        }
    }
}

private class ScriptedMigrationLease(
    private val outcomesByModelId: MutableMap<UInt, ArrayDeque<Boolean>> = mutableMapOf(),
) : MigrationLease {
    val tryAcquireCalls = atomic(0)
    val releaseCalls = atomic(0)

    override suspend fun tryAcquire(modelId: UInt, migrationId: String): Boolean {
        tryAcquireCalls.incrementAndGet()
        return outcomesByModelId[modelId]?.removeFirstOrNull() ?: true
    }

    override suspend fun release(modelId: UInt, migrationId: String) {
        releaseCalls.incrementAndGet()
    }
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
    val baseRef by reference(index = 1u, required = false, dataModel = { Phase6OrderBaseV1 })
    val value by string(index = 2u)
}

private object Phase6OrderDependentV2 : RootDataModel<Phase6OrderDependentV2>(
    name = "Phase6OrderDependent",
    version = Version(2),
) {
    val baseRef by reference(index = 1u, required = false, dataModel = { Phase6OrderBaseV2 })
    val value by number(index = 2u, type = SInt32, required = true)
}

private object Phase6CycleLeftModel : RootDataModel<Phase6CycleLeftModel>(
    name = "Phase6CycleLeftModel",
    version = Version(1),
) {
    val rightRef by reference(index = 1u, required = false, dataModel = { Phase6CycleRightModel })
}

private object Phase6CycleRightModel : RootDataModel<Phase6CycleRightModel>(
    name = "Phase6CycleRightModel",
    version = Version(1),
) {
    val leftRef by reference(index = 1u, required = false, dataModel = { Phase6CycleLeftModel })
}
