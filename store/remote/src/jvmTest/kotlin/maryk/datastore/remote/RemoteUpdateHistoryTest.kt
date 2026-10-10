package maryk.datastore.remote

import java.net.ServerSocket
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.getUpdateHistory
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.datastore.memory.InMemoryDataStore
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RemoteUpdateHistoryTest {
    @Test
    fun getAndScanHistoryContinueAcrossTransport() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(
            keepAllVersions = true, keepUpdateHistoryIndex = true,
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteStoreServer(store).start("127.0.0.1", port, wait = false)
        val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
        try {
            val key = assertIs<AddSuccess<SimpleMarykModel>>(
                remote.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha first" })).statuses.single()
            ).key
            val version = assertIs<ChangeSuccess<SimpleMarykModel>>(
                remote.execute(SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "ha second")))).statuses.single()
            ).version
            val request = SimpleMarykModel.getUpdateHistory(key, limit = 1u)
            val first = remote.execute(request)
            assertEquals(version, assertIs<ChangeUpdate<SimpleMarykModel>>(first.updates.single()).version)
            val second = remote.execute(request.copy(cursor = assertNotNull(first.nextCursor)))
            assertEquals(key, assertIs<AdditionUpdate<SimpleMarykModel>>(second.updates.single()).key)
            assertNull(second.nextCursor)

            val scan = remote.execute(SimpleMarykModel.scanUpdateHistory(limit = 1u))
            assertEquals(version, scan.updates.single().version)
            val nextScan = remote.execute(SimpleMarykModel.scanUpdateHistory(limit = 1u, cursor = assertNotNull(scan.nextCursor)))
            assertIs<AdditionUpdate<SimpleMarykModel>>(nextScan.updates.single())
            assertNull(nextScan.nextCursor)
        } finally {
            remote.close()
            server.stop(500, 500)
            store.close()
        }
    }
}
