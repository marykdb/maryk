package io.maryk.app.state

import androidx.compose.ui.text.input.TextFieldValue
import io.maryk.app.config.StoreConnector
import io.maryk.app.ui.sql.sqlResultsTsv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.TransactionalDataStore
import maryk.core.query.requests.TransactionRequest
import maryk.sql.SqlExecutionStatus
import maryk.sql.SqlValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private object ConsoleItem : RootDataModel<ConsoleItem>(keyDefinition = { ConsoleItem.id.ref() }) {
    val id by number(1u, UInt64, final = true)
    val name by string(2u, required = false)
}

class SqlConsoleStateTest {
    private suspend fun SqlConsoleState.finished() = withTimeout(5000) { while (running) yield() }

    @Test fun enteringTimeTravelClearsPersistedWriteMode() = runBlocking {
        val browser = BrowserState(StoreConnector(), this)
        browser.sqlConsole.editor = TextFieldValue("SELECT 1")
        browser.sqlConsole.writeMode = true
        browser.sqlConsole.cancel() // Closing the modal retains its draft and mode.
        browser.updateTimeTravelEnabled(true)
        assertTrue(browser.timeTravelEnabled)
        assertFalse(browser.sqlConsole.writeMode)
        assertEquals("SELECT 1", browser.sqlConsole.editor.text)
        browser.updateTimeTravelEnabled(false)
        assertFalse(browser.sqlConsole.writeMode)
    }

    @Test fun readsWritesParametersAndErrorsAreVisible() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ConsoleItem))
        val state = SqlConsoleState(this)
        try {
            state.attach(store)
            state.editor = TextFieldValue("INSERT INTO ConsoleItem (id, name) VALUES (1, 'Ada')")
            state.run(0)
            state.finished()
            assertNotNull(state.error)
            state.writeMode = true
            state.run(0)
            state.finished()
            assertEquals("Affected rows: 1", state.message)
            state.writeMode = false
            state.editor = TextFieldValue("SELECT name, NULL, 'NULL' FROM ConsoleItem WHERE id = ?")
            state.updateParameter(0, SqlParameterInput(SqlParameterKind.UNSIGNED, "1"))
            state.run(1)
            state.finished()
            assertEquals(null, state.error)
            assertEquals(SqlExecutionStatus.SUCCEEDED, state.summary?.status)
            assertEquals(SqlValue.Text("Ada"), state.rows.single()[0])
            assertTrue(sqlResultsTsv(state.columns.map { it.name }, state.rows).endsWith("'Ada'\tNULL\t'NULL'"))
            state.editor = TextFieldValue("SELECT FROM")
            state.run(0)
            state.finished()
            assertNotNull(state.errorPosition)
            state.selectError()
            assertEquals(state.errorPosition, state.editor.selection.start)
        } finally { state.detach(); store.close() }
    }

    @Test fun aggregateArithmeticInitializesFloatingParameters() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ConsoleItem))
        val state = SqlConsoleState(this)
        try {
            state.attach(store)
            state.editor = TextFieldValue("SELECT SUM(?) + 1.0e0")
            state.initializeParameters(1)
            assertEquals(SqlParameterKind.FLOAT, state.parameters.single().kind)
        } finally { state.detach(); store.close() }
    }

    @Test fun cancellationBeforeDispatchAndDetachCannotLeaveRunningOrStaleResults() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ConsoleItem))
        val slow = object : IsDataStore by store {
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(request: RQ): RP {
                delay(1000)
                return store.execute(request)
            }
        }
        val owner = Job()
        val state = SqlConsoleState(CoroutineScope(coroutineContext + owner))
        try {
            state.attach(slow)
            state.editor = TextFieldValue("SELECT id FROM ConsoleItem")
            state.run(0)
            state.cancel()
            state.finished()
            assertTrue(state.message.startsWith("Cancelled"))
            state.run(0)
            state.detach()
            owner.children.toList().forEach { it.join() }
            assertFalse(state.running)
            assertTrue(state.rows.isEmpty())
            assertEquals("Connect a store to run SQL.", state.message)
        } finally { owner.cancel(); state.detach(); store.close() }
    }

    @Test fun writesRejectReadConsistencyOptionsAndParametersAreStrict() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ConsoleItem))
        val state = SqlConsoleState(this)
        try {
            state.attach(store)
            state.writeMode = true
            state.snapshot = true
            state.editor = TextFieldValue("INSERT INTO ConsoleItem (id) VALUES (1)")
            state.run(0)
            assertFalse(state.running)
            assertTrue(state.error.orEmpty().contains("Turn off writes"))
            state.writeMode = false
            state.snapshot = false
            state.editor = TextFieldValue("SELECT ?")
            state.updateParameter(0, SqlParameterInput(SqlParameterKind.BOOLEAN, "not-a-bool"))
            state.run(1)
            assertTrue(state.error.orEmpty().contains("Parameter 1"))
        } finally { state.detach(); store.close() }
    }
    @Test fun uncertainWritesMustResolveBeforeAnotherWrite() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ConsoleItem))
        var lose = true
        val transport = object : IsDataStore by store, TransactionalDataStore by store {
            override suspend fun executeTransaction(request: TransactionRequest) = store.executeTransaction(request).also {
                if (request.commit && lose) { lose = false; error("Lost response") }
            }
        }
        val state = SqlConsoleState(this)
        try {
            state.attach(transport)
            state.writeMode = true
            state.editor = TextFieldValue("INSERT INTO ConsoleItem (id) VALUES (1) RETURNING id")
            state.run(0)
            state.finished()
            assertNotNull(state.pendingCommit)
            state.editor = TextFieldValue("INSERT INTO ConsoleItem (id) VALUES (2)")
            state.run(0)
            assertFalse(state.running)
            assertTrue(state.error.orEmpty().contains("Resolve"))
            state.resolveCommit()
            state.finished()
            assertEquals(null, state.pendingCommit)
            assertEquals("Commit confirmed.", state.message)
            state.writeMode = false
            state.editor = TextFieldValue("SELECT id FROM ConsoleItem")
            state.run(0)
            state.finished()
            assertEquals(listOf(SqlValue.UInt64(1u)), state.rows.map { it[0] })
        } finally { state.detach(); store.close() }
    }

}
