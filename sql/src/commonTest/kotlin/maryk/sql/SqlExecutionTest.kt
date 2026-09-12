package maryk.sql

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import maryk.core.query.requests.add
import maryk.datastore.memory.InMemoryDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlExecutionTest {
    @Test
    fun preparedExecutionsAreRepeatableAndIndividualFlowsAreSingleUse() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            val prepared = MarykSql.create(store).prepare("SELECT ? AS value")
            val values = mutableListOf<SqlValue>(SqlValue.Int64(7))
            val execution = prepared.execute(values)
            values[0] = SqlValue.Int64(9)
            assertFailsWith<SqlException> { execution.awaitCompletion() }
            assertEquals(SqlValue.Int64(7), execution.rows.toList().single()[0])
            assertEquals(SqlExecutionStatus.SUCCEEDED, execution.awaitCompletion().status)
            assertFailsWith<SqlException> { execution.rows.toList() }
            assertEquals(SqlValue.Int64(9), prepared.execute(values).rows.toList().single()[0])
        } finally { store.close() }
    }

    @Test
    fun cancellationAndLimitHaveDifferentCompletionStatesAndKeepStoreOpen() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            for (i in 1..5) store.execute(SqlTestModel.add(SqlTestModel.create { id with i.toULong() }))
            val sql = MarykSql.create(store, options = SqlOptions(allowTableScan = true, pageSize = 1))
            val cancelled = sql.prepare("SELECT id FROM SqlTestModel").execute()
            cancelled.cancel()
            assertEquals(SqlExecutionStatus.CANCELLED, cancelled.awaitCompletion().status)
            assertEquals(0, cancelled.awaitCompletion().storeRequests)
            val taken = sql.prepare("SELECT id FROM SqlTestModel").execute()
            taken.rows.take(1).toList()
            assertEquals(SqlExecutionStatus.CANCELLED, taken.awaitCompletion().status)
            val limited = sql.prepare("SELECT id FROM SqlTestModel LIMIT 1").execute()
            assertEquals(1, limited.rows.toList().size)
            assertEquals(SqlExecutionStatus.SUCCEEDED, limited.awaitCompletion().status)
            assertEquals(1, limited.awaitCompletion().storeRequests)
            assertEquals(5, sql.query("SELECT id FROM SqlTestModel").rows.size)
        } finally { store.close() }
    }

    @Test
    fun zeroLimitAvoidsScansAndBudgetExhaustionIsExplicit() = sqlTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SqlTestModel))
        try {
            for (i in 1..3) store.execute(SqlTestModel.add(SqlTestModel.create { id with i.toULong() }))
            val zero = MarykSql.create(store).prepare("SELECT id FROM SqlTestModel LIMIT 0").execute()
            assertTrue(zero.rows.toList().isEmpty())
            assertEquals(0, zero.awaitCompletion().storeRequests)
            val limited = MarykSql.create(store, options = SqlOptions(allowTableScan = true, pageSize = 1, maxFetchedRows = 2))
                .prepare("SELECT id FROM SqlTestModel ORDER BY id").execute()
            val error = assertFailsWith<SqlException> { limited.rows.toList() }
            assertEquals(SqlErrorCode.LIMIT, error.code)
            assertEquals(SqlExecutionStatus.FAILED, limited.awaitCompletion().status)
        } finally { store.close() }
    }
}
