package maryk.datastore.rocksdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationConfiguration
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationOutcome
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.SInt64
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.model.modelMigrationStateKey
import maryk.deleteFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun failedMigrationRejectsSourceReopenBeforeHooksAndResumesTarget() = runTest {
        for (history in listOf(false, true)) for (sameVersion in listOf(false, true)) {
            val path = createTestDBFolder("pendingMigration-$history-$sameVersion")
            val target = PendingTarget(Version(if (sameVersion) 1 else 2))
            try {
                RocksDBDataStore.open(relativePath = path, keepAllVersions = history,
                    dataModelsById = mapOf(1u to PendingSource)).close()
                assertFailsWith<MigrationException> {
                    RocksDBDataStore.open(relativePath = path, keepAllVersions = history,
                        dataModelsById = mapOf(1u to target), migrationConfiguration = MigrationConfiguration(
                            migrationAuditEventReporter = {},
                            migrationHandler = { context ->
                                assertIs<AddSuccess<PendingTarget>>(context.store.execute(
                                    target.add(target.create { value with 123456789 })
                                ).statuses.single())
                                MigrationOutcome.Fatal("injected failure after target data commit")
                            },
                        ))
                }
                assertFailsWith<MigrationException> {
                    RocksDBDataStore.open(relativePath = path, keepAllVersions = history,
                        dataModelsById = mapOf(1u to PendingOtherTarget(target.Meta.version)),
                        migrationConfiguration = MigrationConfiguration(migrationHandler = {
                            error("A different target with the same version must never run")
                        }))
                }
                var hookCalls = 0
                for (versionOnly in listOf(false, true)) {
                    assertFailsWith<MigrationException> {
                        RocksDBDataStore.open(relativePath = path, keepAllVersions = history,
                            dataModelsById = mapOf(1u to PendingSource), onlyCheckModelVersion = versionOnly,
                            versionUpdateHandler = { _, _, _ -> hookCalls++ })
                    }
                }
                assertEquals(0, hookCalls)
                val resumed = RocksDBDataStore.open(relativePath = path, keepAllVersions = history,
                    dataModelsById = mapOf(1u to target), migrationConfiguration = MigrationConfiguration(
                        migrationAuditEventReporter = {}, migrationHandler = { MigrationOutcome.Success },
                    ))
                try {
                    assertEquals(123456789, resumed.execute(target.scan(allowTableScan = true)).values.single().values { value })
                } finally { resumed.close() }
                RocksDBDataStore.open(relativePath = path, keepAllVersions = history,
                    dataModelsById = mapOf(1u to target)).close()
            } finally { deleteFolder(path) }
        }
    }

    @Test
    fun migrationCallbackCannotBypassAnotherStoresPendingMigration() = runTest {
        val firstPath = createTestDBFolder("migrationContextOwnerFirst")
        val secondPath = createTestDBFolder("migrationContextOwnerSecond")
        val target = PendingTarget(Version(2))
        val releaseSecond = CompletableDeferred<Unit>()
        try {
            for (path in listOf(firstPath, secondPath)) {
                RocksDBDataStore.open(relativePath = path, dataModelsById = mapOf(1u to PendingSource)).close()
            }
            val second = RocksDBDataStore.open(relativePath = secondPath, dataModelsById = mapOf(1u to target),
                migrationConfiguration = MigrationConfiguration(
                    migrationAuditEventReporter = {}, migrationStartupBudgetMs = -1L,
                    continueMigrationsInBackground = true,
                    migrationHandler = { releaseSecond.await(); MigrationOutcome.Success },
                ))
            try {
                RocksDBDataStore.open(relativePath = firstPath, dataModelsById = mapOf(1u to target),
                    migrationConfiguration = MigrationConfiguration(migrationAuditEventReporter = {}, migrationHandler = { context ->
                        assertEquals(0, context.store.execute(target.scan(allowTableScan = true)).values.size)
                        assertFailsWith<RequestException> { second.execute(target.scan(allowTableScan = true)) }
                        MigrationOutcome.Success
                    })).close()
                releaseSecond.complete(Unit)
                withContext(Dispatchers.Default) { withTimeout(5_000) { second.awaitMigration(1u) } }
            } finally { releaseSecond.complete(Unit); second.close() }
        } finally { deleteFolder(firstPath); deleteFolder(secondPath) }
    }

    @Test
    fun oldUnboundProgressCannotInvokeTheConfiguredTarget() = runTest {
        val path = createTestDBFolder("pendingMigrationWithoutTarget")
        val store = RocksDBDataStore.open(relativePath = path, dataModelsById = mapOf(1u to PendingSource))
        try {
            store.db.put(store.getColumnFamilies(1u).model, modelMigrationStateKey, MigrationState(
                "PendingRecord:1.0->2.0", MigrationPhase.Backfill, MigrationStateStatus.Failed,
                2u, "1.0", "2.0",
            ).toPersistedBytes())
        } finally { store.close() }
        try {
            val failure = assertFailsWith<MigrationException> {
                RocksDBDataStore.open(relativePath = path, dataModelsById = mapOf(1u to PendingTarget(Version(2))),
                    migrationConfiguration = MigrationConfiguration(migrationHandler = {
                        error("An unbound checkpoint cannot authorize callbacks")
                    }))
            }
            assertTrue(failure.message.orEmpty().contains("authoritative"))
        } finally { deleteFolder(path) }
    }

    @Test
    fun publishedTargetClearsOnlyAnObsoleteFinalizationMarker() = runTest {
        val path = createTestDBFolder("pendingMigrationPublished")
        val target = PendingTarget(Version(2))
        try {
            val store = RocksDBDataStore.open(relativePath = path, dataModelsById = mapOf(1u to target))
            try {
                store.db.put(store.getColumnFamilies(1u).model, modelMigrationStateKey, MigrationState(
                    migrationId = "PendingRecord:1.0->2.0", phase = MigrationPhase.Contract,
                    status = MigrationStateStatus.Running, attempt = 4u, fromVersion = "1.0", toVersion = "2.0",
                    message = "Migration phases complete; finalization pending",
                ).toPersistedBytes())
            } finally { store.close() }
            val reopened = RocksDBDataStore.open(relativePath = path, dataModelsById = mapOf(1u to target))
            try {
                assertNull(reopened.db.get(reopened.getColumnFamilies(1u).model, modelMigrationStateKey))
            } finally { reopened.close() }
        } finally { deleteFolder(path) }
    }
}
