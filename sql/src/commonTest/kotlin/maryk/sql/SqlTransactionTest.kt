package maryk.sql

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.TransactionRequest
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.TransactionalDataStore

import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.TransactionStatus
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.TransactionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlTransactionTest {
    private suspend fun withSql(block: suspend (MarykSql) -> Unit) {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try { block(MarykSql.create(store, SqlCatalog(listOf(SqlTable("items", SqlTestModel))), SqlOptions(allowTableScan = true))) }
        finally { store.close() }
    }

    @Test
    fun queriesMutationsAndReturningShareOneTransaction() = sqlTest {
        withSql { sql ->
            val tx = sql.beginTransaction()
            val insert = tx.execute("INSERT INTO items (id, category) VALUES (1, 'first'), (2, 'first') RETURNING id, __version")
            assertEquals(AtomicStatus.Staged, insert.response.status)
            assertTrue(sql.query("SELECT id FROM items").rows.isEmpty())
            assertEquals(2, tx.query("SELECT id FROM items").rows.size)
            assertEquals(2, tx.execute("UPDATE items SET category = 'second' WHERE category = 'first' RETURNING category").affectedRows)
            assertEquals(2, tx.execute("UPDATE items SET category = 'third' WHERE category = 'second'").affectedRows)
            assertEquals(SqlValue.Text("third"), tx.query("SELECT category FROM items WHERE id = 1").rows.single()[0])
            assertEquals(TransactionStatus.Committed, tx.commit().status)
            assertEquals(2, sql.query("SELECT id FROM items WHERE category = 'third'").rows.size)
        }
    }

    @Test
    fun autocommitSupportsPredicateWritesHardDeleteAndDurableReturning() = sqlTest {
        withSql { sql ->
            val inserted = sql.execute("INSERT INTO items (id, category) VALUES (1, 'a'), (2, 'a') RETURNING id, __version")
            assertEquals(AtomicStatus.Committed, inserted.response.status)
            val actual = sql.query("SELECT id, __version FROM items ORDER BY id")
            assertEquals(actual.rows.map { it.values }, inserted.returning.single().rows.map { it.values })
            assertEquals(2, sql.execute("UPDATE items SET category = 'b' WHERE category = 'a'").affectedRows)
            assertEquals(2, sql.execute("DELETE HARD FROM items WHERE category = 'b'").affectedRows)
            assertTrue(sql.query("SELECT id FROM items").rows.isEmpty())
        }
    }

    @Test
    fun sqlControlCommandsRollbackSavepointsAndEntireTransactions() = sqlTest {
        withSql { sql ->
            val session = sql.session()
            session.execute("/* scope */ BEGIN TRANSACTION;")
            session.execute("INSERT INTO items (id, category) VALUES (1, 'keep')")
            session.execute("SAVEPOINT step")
            session.execute("UPDATE items SET category = 'discard' WHERE id = 1")
            session.execute("ROLLBACK TO SAVEPOINT step")
            val row = session.execute("SELECT category FROM items") as SqlSessionResult.Query
            assertEquals(SqlValue.Text("keep"), row.result.rows.single()[0])
            session.execute("RELEASE SAVEPOINT step")
            val commit = session.execute("COMMIT") as SqlSessionResult.Control
            assertEquals(TransactionStatus.Committed, commit.commit?.status)
            session.execute("START TRANSACTION")
            session.execute("DELETE HARD FROM items WHERE id = 1")
            session.execute("ROLLBACK")
            assertEquals(1, sql.query("SELECT id FROM items").rows.size)
            session.close()
        }
    }

    @Test
    fun statementFailureCanRecoverToASavepoint() = sqlTest {
        withSql { sql ->
            val tx = sql.beginTransaction()
            tx.execute("INSERT INTO items (id) VALUES (1)")
            val point = tx.savepoint()
            assertFailsWith<TransactionException> { tx.execute("INSERT INTO items (id) VALUES (1)") }
            tx.rollbackTo(point)
            tx.execute("INSERT INTO items (id) VALUES (2)")
            assertEquals(TransactionStatus.Committed, tx.commit().status)
            assertEquals(2, sql.query("SELECT id FROM items").rows.size)
        }
    }
    @Test
    fun retainedHistoryReadsWorkInsideTheTransaction() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel), keepAllVersions = true)
        try {
            val sql = MarykSql.create(store, SqlCatalog(listOf(SqlTable("items", SqlTestModel))), SqlOptions(allowTableScan = true))
            sql.execute("INSERT INTO items (id, category) VALUES (1, 'original')")
            val version = (sql.query("SELECT __version FROM items").rows.single()[0] as SqlValue.UInt64).value
            val tx = sql.beginTransaction()
            tx.execute("UPDATE items SET category = 'staged' WHERE id = 1")
            assertEquals(SqlValue.Text("original"), tx.query("SELECT category FROM items", readOptions = SqlReadOptions(SqlConsistency.AtVersion(version))).rows.single()[0])
            assertEquals(SqlValue.Text("staged"), tx.query("SELECT category FROM items").rows.single()[0])
            assertEquals(TransactionStatus.Committed, tx.commit().status)
        } finally { store.close() }
    }

    @Test
    fun commitWaitsForTheWholePagedSqlQuery() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        var pause = false
        val transport = object : IsDataStore by store, TransactionalDataStore by store {
            override suspend fun executeTransaction(request: TransactionRequest) = store.executeTransaction(request).also {
                if (pause && !request.commit && request.requests.lastOrNull() is ScanRequest<*>) {
                    pause = false
                    entered.complete(Unit)
                    resume.await()
                }
            }
        }
        try {
            val sql = MarykSql.create(transport, SqlCatalog(listOf(SqlTable("items", SqlTestModel))), SqlOptions(allowTableScan = true, pageSize = 1))
            sql.execute("INSERT INTO items (id) VALUES (1), (2), (3)")
            val tx = sql.beginTransaction()
            pause = true
            coroutineScope {
                val query = async { tx.query("SELECT id FROM items ORDER BY id") }
                entered.await()
                val commit = async(start = CoroutineStart.UNDISPATCHED) { tx.commit() }
                assertTrue(!commit.isCompleted)
                resume.complete(Unit)
                assertEquals(3, query.await().rows.size)
                assertEquals(TransactionStatus.Committed, commit.await().status)
            }
        } finally { resume.complete(Unit); store.close() }
    }

    @Test
    fun emptyAtomicSqlJoinsTheScopeAndStillRejectsReadOnlyExecution() = sqlTest {
        withSql { sql ->
            val prepared = sql.prepareAtomic("no-targets", "UPDATE items SET category = 'none' WHERE id = 99")
            val staged = sql.transaction { assertEquals(AtomicStatus.Staged, prepared.execute().response.status) }
            assertEquals(TransactionStatus.Committed, staged.commit.status)
            val readonly = sql.transaction(readOnly = true) {
                assertFailsWith<SqlException> { prepared.execute() }
                assertFailsWith<SqlException> { sql.executeAtomic("readonly-no-targets", "DELETE FROM items WHERE id = 99") }
            }
            assertEquals(TransactionStatus.Committed, readonly.commit.status)
        }
    }

}
