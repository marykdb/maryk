package maryk.datastore.remote

import maryk.core.models.migration.MigrationMetrics
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationRuntimeState
import maryk.core.models.migration.MigrationRuntimeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteMigrationAdminCodecTest {
    @Test
    fun roundTripsVersionedRequestsAndResponses() {
        val request = RemoteMigrationRequest(
            operation = RemoteMigrationOperation.Cancel,
            modelId = 3u,
            reason = "operator requested | now",
        )
        assertEquals(request, RemoteMigrationAdminCodec.decodeRequest(RemoteMigrationAdminCodec.encodeRequest(request)))

        val response = RemoteMigrationResponse(
            statuses = mapOf(
                3u to MigrationRuntimeStatus(
                    state = MigrationRuntimeState.Paused,
                    message = "waiting | safely",
                    phase = MigrationPhase.Backfill,
                    attempt = 2u,
                    hasCursor = true,
                    etaMs = 500,
                )
            ),
            metrics = mapOf(3u to MigrationMetrics(completed = 1u, retries = 2u)),
        )
        assertEquals(response, RemoteMigrationAdminCodec.decodeResponse(RemoteMigrationAdminCodec.encodeResponse(response)))
    }

    @Test
    fun rejectsMalformedAdministrativePayloads() {
        assertFailsWith<IllegalArgumentException> {
            RemoteMigrationAdminCodec.decodeRequest("v=1\\nop=Pause\\nmodel=invalid".encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteMigrationAdminCodec.decodeRequest("v=1\\nop=Status\\nmodel=3".encodeToByteArray())
        }
        listOf(
            "v=1\\nop=Status\\nmalformed",
            "v=1\\nop=Status\\nunknown=value",
            "v=1\\nop=Status\\nop=Pause",
            "v=1\\nop=Status\\nv=1",
        ).forEach { payload ->
            assertFailsWith<IllegalArgumentException> {
                RemoteMigrationAdminCodec.decodeRequest(payload.encodeToByteArray())
            }
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteMigrationAdminCodec.decodeResponse("v=1\\naccepted=unknown".encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteMigrationAdminCodec.decodeResponse("v=1\\ns|3|unknown||||||".encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteMigrationAdminCodec.decodeResponse(
                "v=1".encodeToByteArray(),
                RemoteMigrationOperation.Pause,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RemoteMigrationAdminCodec.decodeResponse(
                "v=1\\naccepted=true".encodeToByteArray(),
                RemoteMigrationOperation.Status,
            )
        }
    }

    @Test
    fun rejectsDuplicateMigrationResponseFields() {
        listOf(
            "v=1\naccepted=true\naccepted=false",
            "v=1\ns|3|Running||||||\ns|3|Paused||||||",
            "v=1\nm|3|0|0|0|0|0|0|0|0|\nm|3|1|0|0|0|0|0|0|0|",
        ).forEach { payload ->
            assertFailsWith<IllegalArgumentException> {
                RemoteMigrationAdminCodec.decodeResponse(payload.encodeToByteArray())
            }
        }
    }
}
