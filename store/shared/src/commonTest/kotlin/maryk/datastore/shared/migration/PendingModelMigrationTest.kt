package maryk.datastore.shared.migration

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Version
import maryk.core.properties.types.numeric.SInt32
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object PendingSource : RootDataModel<PendingSource>(name = "PendingRecord", version = Version(1)) {
    val value by string(index = 1u)
}

private class PendingTarget(version: Version) : RootDataModel<PendingTarget>(name = "PendingRecord", version = version) {
    val value by number(index = 1u, type = SInt32)
}

class PendingModelMigrationTest {
    private val target = PendingTarget(Version(2))
    private val state = MigrationState("PendingRecord:1.0->2.0", MigrationPhase.Backfill,
        MigrationStateStatus.Failed, 2u, "1.0", "2.0")

    @Test
    fun pendingProgressRequiresTheRecordedTransition() = runTest {
        val identity = migrationTargetIdentity(target)
        assertFalse(validatePendingModelMigration(state, PendingSource, target, identity))
        assertFailsWith<MigrationException> { validatePendingModelMigration(state, PendingSource, target, null) }
        assertFailsWith<MigrationException> { validatePendingModelMigration(state, PendingSource, PendingSource, identity) }
        assertFailsWith<MigrationException> { validatePendingModelMigration(state, null, target, identity) }
        assertFailsWith<MigrationException> { validatePendingModelMigration(state, target, target, identity) }
        assertFailsWith<MigrationException> { validatePendingModelMigration(state, PendingSource, PendingTarget(Version(3)), identity) }
    }

    @Test
    fun onlyPublishedFinalizationCanBeCleared() = runTest {
        val identity = migrationTargetIdentity(target)
        val finalization = state.copy(phase = MigrationPhase.Contract, status = MigrationStateStatus.Running,
            message = "Migration phases complete; finalization pending")
        assertFalse(validatePendingModelMigration(finalization, PendingSource, target, identity))
        assertTrue(validatePendingModelMigration(finalization, target, target, identity))
        assertFailsWith<MigrationException> { validatePendingModelMigration(finalization, PendingSource, PendingSource, identity) }
        assertFailsWith<MigrationException> { validatePendingModelMigration(finalization.copy(status = MigrationStateStatus.Failed), target, target, identity) }
        val sameVersion = finalization.copy(migrationId = "PendingRecord:2.0->2.0", fromVersion = "2.0")
        assertFailsWith<MigrationException> { validatePendingModelMigration(sameVersion, target, target, null) }
        assertTrue(validatePendingModelMigration(sameVersion, target, target, identity))
    }
}
