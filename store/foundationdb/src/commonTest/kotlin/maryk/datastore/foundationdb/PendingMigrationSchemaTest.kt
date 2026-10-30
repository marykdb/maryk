package maryk.datastore.foundationdb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.StorageException
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.models.migration.MigrationOutcome
import maryk.core.models.migration.NoopMigrationLease
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.SInt64
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.datastore.foundationdb.model.modelMigrationStateKey
import maryk.datastore.foundationdb.processors.helpers.packKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private object PendingSource : RootDataModel<PendingSource>(name = "PendingRecord", version = Version(1)) {
    val value by string(index = 1u)
}

private class PendingTarget(version: Version) : RootDataModel<PendingTarget>(name = "PendingRecord", version = version) {
    val value by number(index = 1u, type = SInt32)
}

private class PendingOtherTarget(version: Version) : RootDataModel<PendingOtherTarget>(name = "PendingRecord", version = version) {
    val value by number(index = 1u, type = SInt64)
}

class PendingMigrationSchemaTest {
    @Test
    fun oldUnboundProgressCannotInvokeTheConfiguredTarget() = runTest(timeout = 3.minutes) {
        val directory = listOf("maryk", "pending-without-target", Uuid.random().toString())
        val store = FoundationDBDataStore.open(directoryPath = directory, dataModelsById = mapOf(1u to PendingSource))
        try {
            store.tc.run { transaction ->
                transaction.set(packKey(store.getTableDirs(1u).modelPrefix, modelMigrationStateKey), MigrationState(
                    "PendingRecord:1.0->2.0", MigrationPhase.Backfill, MigrationStateStatus.Failed,
                    2u, "1.0", "2.0",
                ).toPersistedBytes())
            }
        } finally { store.close() }
        val failure = assertFailsWith<MigrationException> {
            FoundationDBDataStore.open(directoryPath = directory, dataModelsById = mapOf(1u to PendingTarget(Version(2))),
                migrationConfiguration = MigrationConfiguration(migrationHandler = {
                    error("An unbound checkpoint cannot authorize callbacks")
                }))
        }
        assertTrue(failure.message.orEmpty().contains("authoritative"))
    }

    @Test
    fun canceledStartupPhaseClosesItsActorAndCanResume() = runTest(timeout = 3.minutes) {
        val directory = listOf("maryk", "pending-canceled-startup", Uuid.random().toString())
        val target = PendingTarget(Version(2))
        FoundationDBDataStore.open(directoryPath = directory, dataModelsById = mapOf(1u to PendingSource)).close()
        var interruptedStore: FoundationDBDataStore? = null
        assertFailsWith<CancellationException> {
            FoundationDBDataStore.open(directoryPath = directory, dataModelsById = mapOf(1u to target),
                migrationConfiguration = MigrationConfiguration(migrationAuditEventReporter = {}, migrationExpandHandler = { context ->
                    interruptedStore = context.store
                    assertIs<AddSuccess<PendingTarget>>(context.store.execute(
                        target.add(target.create { value with 42 })
                    ).statuses.single())
                    throw CancellationException("injected cancellation after migration write")
                }))
        }
        assertFailsWith<StorageException> {
            checkNotNull(interruptedStore).execute(target.scan(allowTableScan = true))
        }
        val resumed = FoundationDBDataStore.open(directoryPath = directory, dataModelsById = mapOf(1u to target),
            migrationConfiguration = MigrationConfiguration(migrationAuditEventReporter = {}, migrationExpandHandler = { context ->
                assertEquals(42, context.store.execute(target.scan(allowTableScan = true)).values.single().values { value })
                MigrationOutcome.Success
            }))
        try {
            assertEquals(42, resumed.execute(target.scan(allowTableScan = true)).values.single().values { value })
        } finally { resumed.close() }
    }

    @Test
    fun pendingMigrationBlocksOldInstancesAndWrongReopenButResumesTarget() = runTest(timeout = 3.minutes) {
        for (history in listOf(false, true)) for (customLease in listOf(false, true)) {
            val directory = listOf("maryk", "pending-migration", Uuid.random().toString())
            val target = PendingTarget(Version(2))
            val oldStore = FoundationDBDataStore.open(directoryPath = directory, keepAllVersions = history,
                dataModelsById = mapOf(1u to PendingSource))
            try {
                assertFailsWith<MigrationException> {
                    FoundationDBDataStore.open(directoryPath = directory, keepAllVersions = history,
                        dataModelsById = mapOf(1u to target), migrationConfiguration = MigrationConfiguration(
                            migrationAuditEventReporter = {},
                            migrationLease = if (customLease) NoopMigrationLease else null,
                            migrationHandler = { context ->
                                assertIs<AddSuccess<PendingTarget>>(context.store.execute(
                                    target.add(target.create { value with 123456789 })
                                ).statuses.single())
                                assertFailsWith<StorageException> {
                                    oldStore.execute(PendingSource.scan(allowTableScan = true))
                                }
                                val rejected = oldStore.execute(PendingSource.add(PendingSource.create { value with "old-schema write" }))
                                assertTrue(assertIs<ServerFail<PendingSource>>(rejected.statuses.single()).reason.contains("pending migration"))
                                MigrationOutcome.Fatal("injected failure after target data commit")
                            },
                        ))
                }
                assertFailsWith<StorageException> { oldStore.execute(PendingSource.scan(allowTableScan = true)) }
                assertFailsWith<MigrationException> {
                    FoundationDBDataStore.open(directoryPath = directory, keepAllVersions = history,
                        dataModelsById = mapOf(1u to PendingOtherTarget(target.Meta.version)),
                        migrationConfiguration = MigrationConfiguration(migrationHandler = {
                            error("A different target with the same version must never run")
                        }))
                }
                var hookCalls = 0
                for (versionOnly in listOf(false, true)) {
                    assertFailsWith<MigrationException> {
                        FoundationDBDataStore.open(directoryPath = directory, keepAllVersions = history,
                            dataModelsById = mapOf(1u to PendingSource), onlyCheckModelVersion = versionOnly,
                            versionUpdateHandler = { _, _, _ -> hookCalls++ })
                    }
                }
                assertEquals(0, hookCalls)
                val resumed = FoundationDBDataStore.open(directoryPath = directory, keepAllVersions = history,
                    dataModelsById = mapOf(1u to target), migrationConfiguration = MigrationConfiguration(
                        migrationAuditEventReporter = {},
                        migrationLease = if (customLease) NoopMigrationLease else null,
                        migrationHandler = { context ->
                            assertEquals(123456789, context.store.execute(target.scan(allowTableScan = true)).values.single().values { value })
                            MigrationOutcome.Success
                        },
                    ))
                try {
                    assertEquals(123456789, resumed.execute(target.scan(allowTableScan = true)).values.single().values { value })
                    assertFailsWith<StorageException> { oldStore.execute(PendingSource.scan(allowTableScan = true)) }
                } finally { resumed.close() }
                FoundationDBDataStore.open(directoryPath = directory, keepAllVersions = history,
                    dataModelsById = mapOf(1u to target)).close()
            } finally { oldStore.close() }
        }
    }
}
