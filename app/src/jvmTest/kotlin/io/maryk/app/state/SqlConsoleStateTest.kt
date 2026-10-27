package io.maryk.app.state

import androidx.compose.ui.text.input.TextFieldValue
import io.maryk.app.config.StoreConnector
import io.maryk.app.config.StoreDefinition
import io.maryk.app.config.StoreKind
import io.maryk.app.data.DataExportFormat
import io.maryk.app.data.DataImportScope
import io.maryk.app.ui.sql.sqlResultsTsv
import kotlinx.coroutines.CompletableDeferred
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
import maryk.core.query.requests.scan
import maryk.core.query.responses.IsResponse
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.TransactionalDataStore
import maryk.core.query.requests.TransactionRequest
import maryk.sql.SqlExecutionStatus
import maryk.sql.SqlValue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private object ConsoleItem : RootDataModel<ConsoleItem>(keyDefinition = { ConsoleItem.id.ref() }) {
    val id by number(1u, UInt64, final = true)
    val name by string(2u, required = false)
}

class SqlConsoleStateTest {
    private suspend fun SqlConsoleState.finished() = withTimeout(5000) { while (running) yield() }

    @Test fun timeTravelBlocksAllImportEntryPoints() = runBlocking {
        val browser = BrowserState(StoreConnector(), this)
        browser.updateTimeTravelEnabled(true)
        browser.requestImportDataDialog()
        assertNull(browser.dataImportDialog)
        assertEquals("Time travel mode active.", browser.lastActionMessage)
        browser.clearLastActionMessage()
        browser.startImportFromPath("must-not-be-read.json")
        assertFalse(browser.isWorking)
        assertEquals("Time travel mode active.", browser.lastActionMessage)
        browser.clearLastActionMessage()
        browser.importData(1u, DataExportFormat.JSON, DataImportScope.MULTIPLE, "must-not-be-read.json")
        assertFalse(browser.isWorking)
        assertEquals("Time travel mode active.", browser.lastActionMessage)
        browser.updateTimeTravelEnabled(false)
        browser.requestImportDataDialog()
        assertNotNull(browser.dataImportDialog)
    }

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

    @Test fun richerWindowsAndJoinedWritesUseTheConsoleSession() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ConsoleItem))
        val state = SqlConsoleState(this)
        suspend fun run(text: String, write: Boolean = false) {
            state.editor = TextFieldValue(text); state.writeMode = write
            state.run(0); state.finished()
            assertEquals(null, state.error)
        }
        try {
            state.attach(store)
            run("INSERT INTO ConsoleItem (id, name) VALUES (1, 'before')", true)
            run("UPDATE ConsoleItem AS s SET name = v.column2 FROM (VALUES (1, 'after')) v WHERE s.id = v.column1 RETURNING s.name", true)
            assertEquals(SqlValue.Text("after"), state.rows.single()[0])
            run("SELECT COUNT(*) OVER w, LAST_VALUE(column1) OVER w FROM (VALUES (1), (2)) v WINDOW w AS (ORDER BY column1 ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) ORDER BY column1")
            assertEquals(listOf(SqlValue.Int64(1), SqlValue.Int64(2)), state.rows.map { it[0] })
            run("DELETE FROM ConsoleItem AS s USING (VALUES (1)) v WHERE s.id = v.column1 RETURNING s.id", true)
            assertEquals(SqlValue.UInt64(1uL), state.rows.single()[0])
        } finally { state.detach(); store.close() }
    }

    @Test fun mutationsInferTypedParametersBeforeExecution() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ConsoleItem))
        val state = SqlConsoleState(this)
        try {
            state.attach(store)
            state.editor = TextFieldValue("INSERT INTO ConsoleItem (id, name) VALUES (?, ?)")
            state.initializeParameters(2)
            assertEquals(SqlParameterKind.UNSIGNED, state.parameters[0].kind)
            assertEquals(SqlParameterKind.TEXT, state.parameters[1].kind)
        } finally { state.detach(); store.close() }
    }

    @Test fun timeTravelCannotRemoveTheConnectionGuardDuringAWrite() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ConsoleItem))
        val submitted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val transport = object : IsDataStore by store, TransactionalDataStore by store {
            override suspend fun executeTransaction(request: TransactionRequest) = run {
                if (request.commit) { submitted.complete(Unit); release.await() }
                store.executeTransaction(request)
            }
        }
        val browser = BrowserState(StoreConnector(), this)
        val state = browser.sqlConsole
        try {
            state.attach(transport)
            state.writeMode = true
            state.editor = TextFieldValue("INSERT INTO ConsoleItem (id) VALUES (1)")
            state.run(0)
            withTimeout(5000) { submitted.await() }
            browser.updateTimeTravelEnabled(true)
            assertFalse(state.writeMode)
            browser.disconnect()
            var closed = false
            browser.requestClose { closed = true }
            assertFalse(closed)
            assertTrue(state.running)
            assertNotNull(state.catalog)
            assertTrue(state.writeInFlight)
            release.complete(Unit)
            state.finished()
            assertEquals(null, state.error)
            assertFalse(state.writeInFlight)
            browser.requestClose { closed = true }
            assertTrue(closed)
            assertNull(state.catalog)
        } finally { release.complete(Unit); state.detach(); store.close() }
    }

    @Test fun windowCloseRetainsAnUncertainCommitUntilResolution() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ConsoleItem))
        var loseResponse = true
        val transport = object : IsDataStore by store, TransactionalDataStore by store {
            override suspend fun executeTransaction(request: TransactionRequest) = store.executeTransaction(request).also {
                if (request.commit && loseResponse) { loseResponse = false; error("Lost response") }
            }
        }
        val browser = BrowserState(StoreConnector(), this)
        val state = browser.sqlConsole
        try {
            state.attach(transport)
            state.writeMode = true
            state.editor = TextFieldValue("INSERT INTO ConsoleItem (id) VALUES (1)")
            state.run(0)
            state.finished()
            val retained = assertNotNull(state.pendingCommit)
            var closed = false
            browser.requestClose { closed = true }
            assertFalse(closed)
            assertTrue(browser.lastActionMessage.orEmpty().contains("Open SQL console"))
            assertSame(retained, state.pendingCommit)
            assertNotNull(state.catalog)
            state.resolveCommit()
            state.finished()
            assertNull(state.pendingCommit)
            browser.requestClose { closed = true }
            assertTrue(closed)
            assertNull(state.catalog)
        } finally { state.detach(); store.close() }
    }

    @Test fun windowCloseReleasesConnectionAndDiscardsStagedWritesBeforeItsScopeEnds() = runBlocking {
        val folder = Files.createTempDirectory("maryk-browser-close-")
        val owner = Job()
        val browser = BrowserState(StoreConnector(), CoroutineScope(coroutineContext + owner))
        try {
            RocksDBDataStore.open(relativePath = folder.toString(), dataModelsById = mapOf(1u to ConsoleItem)).close()
            browser.connect(StoreDefinition("close-test", "Close test", StoreKind.ROCKS_DB, folder.toString()))
            withTimeout(5000) { while (browser.isWorking) yield() }
            assertNotNull(browser.activeConnection, browser.lastActionMessage)
            val state = browser.sqlConsole
            state.writeMode = true
            for (sql in listOf("BEGIN", "INSERT INTO ConsoleItem (id) VALUES (1)")) {
                state.editor = TextFieldValue(sql)
                state.run(0)
                state.finished()
                assertNull(state.error)
            }
            assertTrue(state.transactionActive)
            var closed = false
            browser.requestClose { closed = true; owner.cancel() }
            assertTrue(closed)
            assertNull(browser.activeConnection)
            assertFalse(state.transactionActive)
            val reopened = RocksDBDataStore.open(relativePath = folder.toString(), dataModelsById = mapOf(1u to ConsoleItem))
            try {
                assertTrue(reopened.execute(ConsoleItem.scan(allowTableScan = true)).values.isEmpty())
            } finally { reopened.close() }
        } finally {
            browser.disconnect()
            owner.cancel()
            folder.toFile().deleteRecursively()
        }
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

    @Test
    fun consoleRetainsExplicitTransactionsAndRollsBackAtDetach() = runBlocking {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ConsoleItem))
        val state = SqlConsoleState(this)
        suspend fun run(text: String, write: Boolean) {
            state.editor = TextFieldValue(text); state.writeMode = write; state.run(0); state.finished()
            assertEquals(null, state.error, state.message)
        }
        try {
            state.attach(store)
            run("BEGIN", true); assertTrue(state.transactionActive)
            run("INSERT INTO ConsoleItem (id, name) VALUES (1, 'Ada')", true)
            run("SELECT name FROM ConsoleItem", false); assertEquals(SqlValue.Text("Ada"), state.rows.single()[0])
            run("COMMIT", true); assertFalse(state.transactionActive)
            run("BEGIN", true)
            run("UPDATE ConsoleItem SET name = 'staged' WHERE id = 1", true)
            state.detach(); state.attach(store)
            run("SELECT name FROM ConsoleItem", false); assertEquals(SqlValue.Text("Ada"), state.rows.single()[0])
        } finally { state.detach(); store.close() }
    }

}
