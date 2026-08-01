package io.maryk.cli.commands

import io.maryk.cli.CliState
import io.maryk.cli.CliEnvironment
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.RocksDbStoreConnection
import maryk.core.models.migration.MigrationMetrics
import maryk.core.models.migration.MigrationRuntimeState
import maryk.core.models.migration.MigrationRuntimeStatus
import maryk.datastore.shared.migration.MigrationAdmin
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationsCommandTest {
    @Test
    fun reportsRemoteAdministrationFailuresAsCommandErrors() {
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", FailingMigrationAdminStore()))
        }
        val context = CommandContext(CommandRegistry(state, migrationTestEnvironment), state, migrationTestEnvironment)

        val status = MigrationsCommand().execute(context, emptyList())
        assertTrue(status.isError)
        assertEquals("Migration status failed: unavailable", status.lines.single())

        val pause = MigrationsCommand().execute(context, listOf("pause", SimpleMarykModel.Meta.name))
        assertTrue(pause.isError)
        assertEquals("Migration pause failed: unavailable", pause.lines.single())
    }
}

private val migrationTestEnvironment = object : CliEnvironment {
    override fun resolveDirectory(path: String): DirectoryResolution = DirectoryResolution.Success(path)
}

private class FailingMigrationAdminStore : FakeDataStore(
    dataModelsById = mapOf(1u to SimpleMarykModel),
), MigrationAdmin {
    override suspend fun getMigrationStatuses(): Map<UInt, MigrationRuntimeStatus> = error("unavailable")

    override suspend fun getMigrationMetrics(): Map<UInt, MigrationMetrics> = error("unavailable")

    override suspend fun requestMigrationPause(modelId: UInt): Boolean = error("unavailable")

    override suspend fun requestMigrationResume(modelId: UInt): Boolean = error("unavailable")

    override suspend fun requestMigrationCancel(modelId: UInt, reason: String): Boolean = error("unavailable")
}
