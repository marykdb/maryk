package maryk.datastore.shared.migration

import maryk.core.models.migration.MigrationAuditEventType
import maryk.core.models.migration.MigrationMetrics
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MigrationRuntimeSupportTest {
    @Test
    fun migrationCountersSaturateAtMaxValue() {
        val metrics = mapOf(
            1u to MigrationMetrics(
                started = UInt.MAX_VALUE,
                completed = UInt.MAX_VALUE,
                failed = UInt.MAX_VALUE,
                retries = UInt.MAX_VALUE,
                partials = UInt.MAX_VALUE,
                paused = UInt.MAX_VALUE,
                resumed = UInt.MAX_VALUE,
                canceled = UInt.MAX_VALUE,
            )
        )

        assertEquals(UInt.MAX_VALUE, updatedMigrationMetrics(metrics, 1u, MigrationAuditEventType.PhaseStarted, 1L)[1u]?.started)
        assertEquals(UInt.MAX_VALUE, updatedMigrationMetrics(metrics, 1u, MigrationAuditEventType.Completed, 1L)[1u]?.completed)
        assertEquals(UInt.MAX_VALUE, updatedMigrationMetrics(metrics, 1u, MigrationAuditEventType.Failed, 1L)[1u]?.failed)
        assertEquals(UInt.MAX_VALUE, updatedMigrationMetrics(metrics, 1u, MigrationAuditEventType.RetryScheduled, 1L)[1u]?.retries)
        assertEquals(UInt.MAX_VALUE, updatedMigrationMetrics(metrics, 1u, MigrationAuditEventType.Partial, 1L)[1u]?.partials)
        assertEquals(UInt.MAX_VALUE, updatedMigrationMetrics(metrics, 1u, MigrationAuditEventType.Paused, 1L)[1u]?.paused)
        assertEquals(UInt.MAX_VALUE, updatedMigrationMetrics(metrics, 1u, MigrationAuditEventType.Resumed, 1L)[1u]?.resumed)
        assertEquals(UInt.MAX_VALUE, updatedMigrationMetrics(metrics, 1u, MigrationAuditEventType.Canceled, 1L)[1u]?.canceled)
    }

    @Test
    fun retryCountSaturatesAtMaxValue() {
        val details = mapOf(
            1u to MigrationRuntimeDetails(
                migrationId = "migration",
                phase = MigrationPhase.Backfill,
                attempt = 1u,
                lastError = null,
                hasCursor = false,
                retryCount = UInt.MAX_VALUE,
                startedAtMs = 1L,
                lastUpdateAtMs = 1L,
            )
        )

        val updated = updatedMigrationRuntimeDetails(
            current = details,
            modelId = 1u,
            state = MigrationState(
                migrationId = "migration",
                phase = MigrationPhase.Backfill,
                status = MigrationStateStatus.Retry,
                attempt = 2u,
                fromVersion = "1",
                toVersion = "2",
            ),
            nowMs = 2L,
        )

        assertEquals(UInt.MAX_VALUE, updated[1u]?.retryCount)
    }

    @Test
    fun nextMigrationAttemptStopsAtMaxValue() {
        assertEquals(1u, nextMigrationAttemptOrNull(null))
        assertEquals(2u, nextMigrationAttemptOrNull(1u))
        assertNull(nextMigrationAttemptOrNull(UInt.MAX_VALUE))
    }
}
