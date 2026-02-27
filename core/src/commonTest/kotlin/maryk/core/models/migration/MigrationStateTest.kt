package maryk.core.models.migration

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MigrationStateTest {
    @Test
    fun roundTripStateEncoding() {
        val state = MigrationState(
            migrationId = "Person:1.0.0->2.0.0",
            phase = MigrationPhase.Migrate,
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
}
