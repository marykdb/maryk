package maryk.core.models.migration

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigrationStateTest {
    @Test
    fun migrationStateEqualityUsesCursorContent() {
        val left = MigrationState(
            migrationId = "Person:1.0.0->2.0.0",
            phase = MigrationPhase.Backfill,
            status = MigrationStateStatus.Partial,
            attempt = 3u,
            fromVersion = "1.0.0",
            toVersion = "2.0.0",
            cursor = byteArrayOf(1, 2, 3),
            message = "resume",
        )
        val right = left.copy(cursor = byteArrayOf(1, 2, 3))

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun migrationOutcomeEqualityUsesCursorContent() {
        assertEquals(
            MigrationOutcome.Partial(byteArrayOf(1, 2, 3), "resume"),
            MigrationOutcome.Partial(byteArrayOf(1, 2, 3), "resume"),
        )
        assertEquals(
            MigrationOutcome.Retry(byteArrayOf(1, 2, 3), "retry", 10),
            MigrationOutcome.Retry(byteArrayOf(1, 2, 3), "retry", 10),
        )
    }

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
        assertEquals(state, decoded)
        assertNotNull(decoded)
        assertContentEquals(state.cursor, decoded.cursor)
    }

    @Test
    fun decodesStateWithoutOptionalFields() {
        val state = MigrationState(
            migrationId = "Person:1.0.0->2.0.0",
            phase = MigrationPhase.Backfill,
            status = MigrationStateStatus.Running,
            attempt = 3u,
            fromVersion = null,
            toVersion = "2.0.0",
        )

        assertEquals(state, MigrationState.fromPersistedBytes(state.toPersistedBytes()))
    }

    @Test
    fun corruptPersistedStateReturnsNull() {
        fun stateWith(line: String) = """
            |v=1
            |migrationId=Person:1.0.0->2.0.0
            |phase=Backfill
            |status=Running
            |attempt=3
            |from=1.0.0
            |to=2.0.0
            |cursor=
            |message=
            |$line
        """.trimMargin().encodeToByteArray()

        assertNull(MigrationState.fromPersistedBytes(stateWith("phase=Unknown")))
        assertNull(MigrationState.fromPersistedBytes(stateWith("status=Unknown")))
        assertNull(MigrationState.fromPersistedBytes(stateWith("attempt=invalid")))
        assertNull(MigrationState.fromPersistedBytes(stateWith("cursor=!")))
        assertNull(MigrationState.fromPersistedBytes(stateWith("message=!")))
    }

    @Test
    fun corruptPersistedAuditEventReturnsNull() {
        val event = MigrationAuditEvent(
            timestampMs = 1L,
            modelId = 1u,
            migrationId = "Person:1.0.0->2.0.0",
            type = MigrationAuditEventType.PhaseStarted,
            phase = MigrationPhase.Backfill,
            attempt = 2u,
            message = "started",
        )
        val line = event.toPersistedLine()

        assertEquals(event, MigrationAuditEvent.fromPersistedLine(line))
        assertNull(MigrationAuditEvent.fromPersistedLine(line.replace("type=PhaseStarted", "type=Unknown")))
        assertNull(MigrationAuditEvent.fromPersistedLine(line.replace("phase=Backfill", "phase=Unknown")))
        assertNull(MigrationAuditEvent.fromPersistedLine(line.replace("attempt=2", "attempt=invalid")))
        assertNull(MigrationAuditEvent.fromPersistedLine(line.replace(Regex("migration=[^;]+"), "migration=!")))
        assertNull(MigrationAuditEvent.fromPersistedLine(line.replace(Regex("message=[^;]+"), "message=!")))
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
