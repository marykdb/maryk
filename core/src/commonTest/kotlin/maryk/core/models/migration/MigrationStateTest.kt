package maryk.core.models.migration

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MigrationStateTest {
    @Test
    fun roundTripStateEncoding() {
        val state = MigrationState(
            migrationId = "Person:1.0.0->2.0.0",
            phase = MigrationPhase.Backfill,
            status = MigrationStateStatus.Partial,
            attempt = 3u,
            fromVersion = "1.0.0",
            toVersion = "2.0.0",
            cursor = byteArrayOf(1, 2, 3),
            message = "resume from cursor",
        )

        val decoded = MigrationState.fromPersistedBytes(state.toPersistedBytes())
        assertNotNull(decoded)
        assertEquals(state.migrationId, decoded.migrationId)
        assertEquals(state.phase, decoded.phase)
        assertEquals(state.status, decoded.status)
        assertEquals(state.attempt, decoded.attempt)
        assertEquals(state.fromVersion, decoded.fromVersion)
        assertEquals(state.toVersion, decoded.toVersion)
        assertContentEquals(state.cursor, decoded.cursor)
        assertEquals(state.message, decoded.message)
    }

    @Test
    fun phaseTransitionsAndCounts() {
        assertEquals(MigrationPhase.Expand, MigrationPhase.Expand.normalizedRuntimePhase())
        assertEquals(MigrationPhase.Backfill, MigrationPhase.Backfill.normalizedRuntimePhase())
        assertEquals(MigrationPhase.Contract, MigrationPhase.Verify.nextRuntimePhaseOrNull())
        assertEquals(null, MigrationPhase.Contract.nextRuntimePhaseOrNull())

        assertTrue(MigrationPhase.Expand.canTransitionTo(MigrationPhase.Backfill))
        assertTrue(MigrationPhase.Backfill.canTransitionTo(MigrationPhase.Verify))
        assertTrue(MigrationPhase.Verify.canTransitionTo(MigrationPhase.Contract))
        assertFalse(MigrationPhase.Expand.canTransitionTo(MigrationPhase.Contract))

        assertEquals(4, MigrationPhase.Expand.remainingRuntimePhaseCount())
        assertEquals(3, MigrationPhase.Backfill.remainingRuntimePhaseCount())
        assertEquals(2, MigrationPhase.Verify.remainingRuntimePhaseCount())
        assertEquals(1, MigrationPhase.Contract.remainingRuntimePhaseCount())
    }
}
