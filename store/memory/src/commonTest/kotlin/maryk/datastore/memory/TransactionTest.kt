package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.exceptions.RequestException
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.TransactionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import maryk.datastore.shared.TransactionCancellationException
import maryk.datastore.shared.TransactionalDataStore
import maryk.datastore.shared.IsDataStore
import maryk.core.query.requests.TransactionCapabilities
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.responses.TransactionResponse
import maryk.datastore.shared.TransactionException
import maryk.datastore.shared.beginTransaction
import maryk.datastore.shared.transactionWithRetry
import maryk.datastore.shared.transaction
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransactionTest {
    private val ref = SimpleMarykModel.ref { value }
    private suspend fun withStore(block: suspend (InMemoryDataStore) -> Unit) {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel), keepAllVersions = false)
        try { block(store) } finally { store.close() }
    }

    @Test
    fun readsOwnWritesAndRepeatedChangesCommitOnce() = runTest {
        withStore { store ->
            val values = SimpleMarykModel.create { value with "haha-initial" }
            val key = SimpleMarykModel.key(values)
            val tx = store.beginTransaction()
            tx.execute(SimpleMarykModel.add(key to values))
            assertTrue(store.execute(SimpleMarykModel.get(key)).values.isEmpty())
            val staged = tx.execute(SimpleMarykModel.get(key)).response.values.single()
            assertEquals("haha-initial", staged.values[ref])
            tx.execute(SimpleMarykModel.change(key.change(Change(ref with "haha-first"), lastVersion = staged.lastVersion)))
            val changed = tx.execute(SimpleMarykModel.get(key)).response.values.single()
            tx.execute(SimpleMarykModel.change(key.change(Change(ref with "haha-final"), lastVersion = changed.lastVersion)))
            assertEquals("haha-final", tx.execute(SimpleMarykModel.get(key)).response.values.single().values[ref])
            val commit = tx.commit()
            assertEquals(TransactionStatus.Committed, commit.status)
            assertEquals("haha-final", store.execute(SimpleMarykModel.get(key)).values.single().values[ref])
            assertEquals(commit, tx.commit())
        }
    }

    @Test
    fun savepointRestoresWritesAndCanBeUsedAgain() = runTest {
        withStore { store ->
            val values = SimpleMarykModel.create { value with "haha-base" }
            val key = SimpleMarykModel.key(values)
            val tx = store.beginTransaction()
            tx.execute(SimpleMarykModel.add(key to values))
            val point = tx.savepoint()
            tx.execute(SimpleMarykModel.change(key.change(Change(ref with "haha-discard"))))
            assertEquals("haha-discard", tx.execute(SimpleMarykModel.get(key)).response.values.single().values[ref])
            tx.rollbackTo(point)
            assertEquals("haha-base", tx.execute(SimpleMarykModel.get(key)).response.values.single().values[ref])
            tx.execute(SimpleMarykModel.change(key.change(Change(ref with "haha-keep"))))
            tx.rollbackTo(point)
            tx.release(point)
            assertEquals(TransactionStatus.Committed, tx.commit().status)
            assertEquals("haha-base", store.execute(SimpleMarykModel.get(key)).values.single().values[ref])
        }
    }

    @Test
    fun rollbackAndBlockFailurePublishNothing() = runTest {
        withStore { store ->
            val tx = store.beginTransaction()
            tx.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-rollback" }))
            tx.rollback()
            assertFailsWith<TransactionException> { tx.commit() }
            assertFailsWith<IllegalStateException> {
                store.transaction { scoped ->
                    scoped.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-failure" }))
                    error("Stop before commit")
                }
            }
            assertTrue(store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
        }
    }

    @Test
    fun concurrentInsertionConflictsWithAnEmptyScan() = runTest {
        withStore { store ->
            val tx = store.beginTransaction()
            assertTrue(tx.execute(SimpleMarykModel.scan(allowTableScan = true)).response.values.isEmpty())
            store.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-phantom" }))
            val commit = tx.commit()
            assertEquals(TransactionStatus.Aborted, commit.status)
            assertEquals(AtomicFailureCode.Conflict, commit.failure?.code)
            assertEquals(1, store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
        }
    }

    @Test
    fun duplicateAfterEarlierWriteAbortsTheCompleteTransaction() = runTest {
        withStore { store ->
            val values = SimpleMarykModel.create { value with "haha-duplicate" }
            val key = SimpleMarykModel.key(values)
            store.execute(SimpleMarykModel.add(key to values))
            val tx = store.beginTransaction()
            tx.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-new" }))
            assertFailsWith<TransactionException> { tx.execute(SimpleMarykModel.add(key to values)) }
            assertEquals(1, store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
        }
    }

    @Test
    fun hardDeleteAndReinsertAreVisibleWithinTheTransaction() = runTest {
        withStore { store ->
            val values = SimpleMarykModel.create { value with "haha-existing" }
            val key = SimpleMarykModel.key(values)
            store.execute(SimpleMarykModel.add(key to values))
            val tx = store.beginTransaction()
            tx.execute(SimpleMarykModel.delete(key, hardDelete = true))
            assertTrue(tx.execute(SimpleMarykModel.get(key)).response.values.isEmpty())
            tx.execute(SimpleMarykModel.add(key to SimpleMarykModel.create { value with "haha-replaced" }))
            assertEquals(TransactionStatus.Committed, tx.commit().status)
            assertEquals("haha-replaced", store.execute(SimpleMarykModel.get(key)).values.single().values[ref])
        }
    }
    @Test
    fun readOnlyScopeRoutesStoreReadsAndRejectsWrites() = runTest {
        withStore { store ->
            val outcome = store.transaction(readOnly = true) { tx ->
                assertTrue(store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
                assertFailsWith<RequestException> { tx.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-denied" })) }
                "read-only"
            }
            assertEquals("read-only", outcome.value)
            assertEquals(TransactionStatus.Committed, outcome.commit.status)
        }
    }

    @Test
    fun scopedStoreWritesRemainPrivateAndRetryIsExplicit() = runTest {
        withStore { store ->
            val outcome = store.transaction {
                store.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-ambient" }))
                assertEquals(1, store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
                "done"
            }
            assertEquals(TransactionStatus.Committed, outcome.commit.status)
            assertEquals(1, store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
            var attempts = 0
            val retried = store.transactionWithRetry { ++attempts }
            assertEquals(1, attempts)
            assertEquals(1, retried.value)
        }
    }

    @Test
    fun lostCommitResponseRetainsAResolvableHandle() = runTest {
        withStore { store ->
            var loseResponse = true
            val transport = object : IsDataStore by store, TransactionalDataStore by store {
                override suspend fun executeTransaction(request: TransactionRequest): TransactionResponse {
                    val response = store.executeTransaction(request)
                    if (request.commit && loseResponse) {
                        loseResponse = false
                        throw CancellationException("Lost the committed response")
                    }
                    return response
                }
            }
            val failure = assertFailsWith<TransactionCancellationException> {
                transport.transaction { tx -> tx.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-lost" })) }
            }
            val resolved = failure.transaction.resolveCommit()
            assertEquals(TransactionStatus.Committed, resolved.status)
            assertEquals(resolved, failure.transaction.resolveCommit())
            assertEquals(1, store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.size)
        }
    }

    @Test
    fun deadlineDiscardsStagedWrites() = runTest {
        withStore { store ->
            val bounded = object : IsDataStore by store, TransactionalDataStore by store {
                override val transactionCapabilities = TransactionCapabilities(store.atomicCapabilities, maxLifetimeMillis = 100u)
            }
            val result = bounded.transaction { tx ->
                tx.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-expired" }))
                delay(200)
            }
            assertEquals(TransactionStatus.Aborted, result.commit.status)
            assertEquals(AtomicFailureCode.Expired, result.commit.failure?.code)
            assertTrue(store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
        }
    }

    @Test
    fun emptyStoresSupportEmptyReadOnlyTransactions() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = emptyMap())
        try {
            val result = store.transaction(readOnly = true) { "empty" }
            assertEquals("empty", result.value)
            assertEquals(TransactionStatus.Committed, result.commit.status)
        } finally { store.close() }
    }

}
