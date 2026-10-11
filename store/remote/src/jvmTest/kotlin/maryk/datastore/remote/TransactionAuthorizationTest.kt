package maryk.datastore.remote

import java.net.ServerSocket
import kotlinx.atomicfu.atomic
import maryk.core.query.pairs.with
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.TransactionException
import maryk.datastore.shared.TransactionalDataStore
import maryk.datastore.shared.beginTransaction
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.Person
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransactionAuthorizationTest {
    @Test
    fun deniedJournalMemberCannotPublishEarlierStaging() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteStoreServer(store).start("127.0.0.1", port, wait = false,
            config = RemoteStoreServerConfig(authorizer = RemoteStoreAuthorizer { it.modelName != Person.Meta.name }))
        try {
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            try {
                val tx = remote.beginTransaction()
                tx.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-allowed" }))
                assertFailsWith<TransactionException> { tx.execute(Person.add(Person.create { firstName with "Denied"; surname with "Person" })) }
                assertTrue(store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
            } finally { remote.close() }
        } finally { server.stop(0, 1000); store.close() }
    }

    @Test
    fun revokedAuthorizationCannotTurnALostCommitIntoAnAbort() = runBoundedIntegrationTest {
        val store = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        val allowed = atomic(true)
        var loseResponse = true
        val transport = object : IsDataStore by store, TransactionalDataStore by store {
            override suspend fun executeTransaction(request: TransactionRequest): TransactionResponse {
                val response = store.executeTransaction(request)
                return if (request.commit && loseResponse) {
                    loseResponse = false
                    response.copy(status = TransactionStatus.OutcomeUnknown, responses = emptyList())
                } else response
            }
        }
        val port = ServerSocket(0).use { it.localPort }
        val server = RemoteStoreServer(transport).start("127.0.0.1", port, wait = false,
            config = RemoteStoreServerConfig(authorizer = RemoteStoreAuthorizer { it.modelName == null || allowed.value }))
        try {
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))
            try {
                val tx = remote.beginTransaction()
                tx.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-committed" }))
                assertEquals(TransactionStatus.OutcomeUnknown, tx.commit().status)
                allowed.value = false
                assertEquals(TransactionStatus.OutcomeUnknown, tx.resolveCommit().status)
                allowed.value = true
                assertEquals(TransactionStatus.Committed, tx.resolveCommit().status)
                assertEquals(1, store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
            } finally { remote.close() }
        } finally { server.stop(0, 1000); store.close() }
    }
}
