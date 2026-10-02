package maryk.datastore.remote

import java.net.ServerSocket
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.requests.CollectRequest
import maryk.core.query.requests.RequestType
import maryk.core.query.requests.Requests
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.memory.InMemoryDataStore
import maryk.sql.MarykSql
import maryk.sql.SqlOptions
import maryk.sql.SqlValue
import maryk.sql.conformance.SqlConformanceItem
import maryk.sql.conformance.sqlConformanceModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SqlAuthorizationTest {
    private suspend fun withRemote(
        denied: RequestType,
        block: suspend (MarykSql, MarykSql, RemoteDataStore, SqlValue.Key) -> Unit,
    ) {
        val store = InMemoryDataStore.open(dataModelsById = sqlConformanceModels)
        val local = MarykSql.create(store, options = SqlOptions(allowTableScan = true))
        local.execute("INSERT INTO SqlConformanceItem (id, category) VALUES (1, 'keep')")
        val key = local.query("SELECT __key FROM SqlConformanceItem").rows.single()[0] as SqlValue.Key
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteStoreServer(store).start("127.0.0.1", port, wait = false,
            config = RemoteStoreServerConfig(authorizer = RemoteStoreAuthorizer { it.requestType != denied }))
        try {
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            try {
                block(local, MarykSql.create(remote, options = SqlOptions(allowTableScan = true)), remote, key)
            } finally { remote.close() }
        } finally { server.stop(0, 1000); store.close() }
    }

    @Test fun deniedDeleteCannotBeBypassedThroughSql() = runBoundedIntegrationTest {
        withRemote(RequestType.Delete) { local, sql, remote, key ->
            val nativeKey = Key<SqlConformanceItem>(key.value.bytes)
            assertIs<AuthFail<*>>(remote.execute(SqlConformanceItem.delete(nativeKey)).statuses.single())
            val result = sql.execute("DELETE FROM SqlConformanceItem WHERE __key = ?", listOf(key))
            assertEquals(0, result.affectedRows)
            assertTrue(result.failures.isNotEmpty())
            assertEquals(1, local.query("SELECT id FROM SqlConformanceItem").rows.size)
            assertEquals(1, sql.execute("UPDATE SqlConformanceItem SET category = 'allowed' WHERE __key = ?", listOf(key)).affectedRows)
            local.execute("DELETE FROM SqlConformanceItem WHERE __key = ?", listOf(key))
            assertIs<ChangeSuccess<*>>(remote.execute(SqlConformanceItem.change(nativeKey.change(ObjectSoftDeleteChange(false)))).statuses.single())
            assertEquals(1, local.query("SELECT id FROM SqlConformanceItem").rows.size)
        }
    }

    @Test fun directAndCollectedSoftDeleteChangesRequireDeletePermission() = runBoundedIntegrationTest {
        withRemote(RequestType.Delete) { local, _, remote, key ->
            val request = SqlConformanceItem.change(Key<SqlConformanceItem>(key.value.bytes).change(ObjectSoftDeleteChange(true)))
            assertIs<AuthFail<*>>(remote.execute(request).statuses.single())
            val collected = remote.execute(Requests(CollectRequest("deleted", request))).single()
            assertIs<AuthFail<*>>(assertIs<ChangeResponse<*>>(collected).statuses.single())
            assertEquals(1, local.query("SELECT id FROM SqlConformanceItem").rows.size)
        }
    }

    @Test fun deniedCollectedDeletionPreventsEarlierBatchChanges() = runBoundedIntegrationTest {
        withRemote(RequestType.Delete) { local, _, remote, key ->
            local.execute("DELETE FROM SqlConformanceItem WHERE __key = ?", listOf(key))
            val nativeKey = Key<SqlConformanceItem>(key.value.bytes)
            val restore = SqlConformanceItem.change(nativeKey.change(ObjectSoftDeleteChange(false)))
            val deletion = SqlConformanceItem.change(nativeKey.change(ObjectSoftDeleteChange(true)))
            val error = assertFailsWith<IllegalStateException> {
                remote.execute(Requests(restore, CollectRequest("deleted", deletion)))
            }
            assertTrue(error.message.orEmpty().contains("403"))
            assertTrue(local.query("SELECT id FROM SqlConformanceItem").rows.isEmpty())
        }
    }

    @Test fun permittedSqlDeleteDoesNotRequireChangePermission() = runBoundedIntegrationTest {
        withRemote(RequestType.Change) { local, sql, _, key ->
            assertEquals(1, sql.execute("DELETE FROM SqlConformanceItem WHERE __key = ?", listOf(key)).affectedRows)
            assertTrue(local.query("SELECT id FROM SqlConformanceItem").rows.isEmpty())
        }
    }
}
