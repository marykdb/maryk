package io.maryk.cli.commands

import io.maryk.cli.BasicCliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.CommandLineParser
import io.maryk.cli.RocksDbStoreConnection
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.requests.TransactionCapabilities
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.responses.TransactionResponse
import maryk.core.query.responses.TransactionStatus
import maryk.core.properties.types.Bytes
import maryk.datastore.shared.TransactionalDataStore
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SqlCommandTest {
    @Test
    fun explicitWritesPreserveSqlAndReportTransactionOutcomes() {
        var calls = 0
        val store = object : FakeDataStore(mapOf(1u to SimpleMarykModel)), TransactionalDataStore {
            override val transactionCapabilities = TransactionCapabilities(AtomicCapabilities())
            override suspend fun executeTransaction(request: TransactionRequest): TransactionResponse {
                if (request.requests.isEmpty()) return TransactionResponse(TransactionStatus.Staged, revision = Bytes(byteArrayOf(0)), version = 1uL)
                val add = assertIs<AddRequest<*>>(request.requests.single())
                assertEquals("ha'quoted", add.objects.first().original(1u))
                val key = SimpleMarykModel.key(add.keysForObjects!!.single().bytes)
                if (!request.commit) return TransactionResponse(TransactionStatus.Staged,
                    listOf(AddResponse(SimpleMarykModel, listOf(AddSuccess(key, 1uL, emptyList())))), version = 1uL)
                calls++
                return if (calls == 1) TransactionResponse(TransactionStatus.Committed,
                    listOf(AddResponse(SimpleMarykModel, listOf(AddSuccess(key, 1uL, emptyList())))), version = 1uL)
                else TransactionResponse(TransactionStatus.Aborted, message = "Already exists")
            }
        }
        val state = CliState().apply { replaceConnection(RocksDbStoreConnection("/test", store)) }
        val context = CommandContext(CommandRegistry(state, BasicCliEnvironment), state, BasicCliEnvironment)
        val statement = "INSERT INTO SimpleMarykModel (value) VALUES ('ha''quoted')"
        assertTrue(SqlCommand().execute(context, listOf(statement)).isError)
        assertEquals(0, calls)
        val parsed = assertIs<CommandLineParser.ParseResult.Success>(CommandLineParser.parse("sql --write -- /* import */ $statement"))
        val success = SqlCommand().execute(context, parsed.tokens.drop(1))
        assertFalse(success.isError, success.lines.joinToString())
        assertEquals(listOf("Committed: 1 affected rows"), success.lines)
        val failure = SqlCommand().execute(context, listOf("--write", statement))
        assertTrue(failure.isError)
        assertEquals("Rejected: 0 affected rows", failure.lines.first())
        assertTrue(failure.lines.last().contains("Already exists"))
        for (option in listOf("--snapshot", "--include-deleted")) {
            assertTrue(SqlCommand().execute(context, listOf("--write", option, statement)).isError)
        }
        assertEquals(2, calls)
    }

    @Test
    fun endOfOptionsPreservesLeadingSqlCommentsAndOutputIsUniformTsv() {
        val state = CliState().apply { replaceConnection(RocksDbStoreConnection("/test", FakeDataStore())) }
        val context = CommandContext(CommandRegistry(state, BasicCliEnvironment), state, BasicCliEnvironment)
        val parsed = assertIs<CommandLineParser.ParseResult.Success>(CommandLineParser.parse("sql -- -- report\nSELECT 1 AS value, 'hello' AS message"))
        val result = SqlCommand().execute(context, parsed.tokens.drop(1))
        assertFalse(result.isError, result.lines.joinToString())
        assertEquals(listOf("value\tmessage", "1\t\"hello\""), result.lines)
    }

    @Test
    fun rejectsConflictingAndRepeatedConsistencyOptions() {
        val state = CliState().apply { replaceConnection(RocksDbStoreConnection("/test", FakeDataStore())) }
        val context = CommandContext(CommandRegistry(state, BasicCliEnvironment), state, BasicCliEnvironment)
        for (options in listOf(
            listOf("--snapshot", "--to-version", "7"),
            listOf("--to-version", "7", "--snapshot"),
            listOf("--snapshot", "--snapshot"),
            listOf("--to-version", "7", "--to-version", "8"),
        )) {
            val result = SqlCommand().execute(context, options + "SELECT 1")
            assertTrue(result.isError)
            assertTrue(result.lines.single().contains("one consistency"), result.lines.joinToString())
        }
    }

    @Test
    fun requiresAConnection() {
        val state = CliState()
        val context = CommandContext(CommandRegistry(state, BasicCliEnvironment), state, BasicCliEnvironment)
        val result = SqlCommand().execute(context, listOf("SELECT 1"))
        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("Not connected"))
    }

    @Test
    fun formatsQuotedStringsAndNullAndReportsErrors() {
        val state = CliState().apply { replaceConnection(RocksDbStoreConnection("/test", FakeDataStore())) }
        val context = CommandContext(CommandRegistry(state, BasicCliEnvironment), state, BasicCliEnvironment)
        val result = SqlCommand().execute(context, listOf("SELECT 'it''s  okay' AS message, NULL AS missing"))
        assertFalse(result.isError)
        assertTrue(result.lines.any { it.contains("message") && it.contains("missing") })
        assertTrue(result.lines.any { it.contains("it's  okay") && it.contains("NULL") })
        val error = SqlCommand().execute(context, listOf("DELETE FROM items"))
        assertTrue(error.isError)
        assertTrue(error.lines.first().contains("SQL"))
    }
    @Test
    fun retainsUncertainCommitAndBlocksFreshWritesUntilResolution() {
        var submitted = 0
        val store = object : FakeDataStore(mapOf(1u to SimpleMarykModel)), TransactionalDataStore {
            override val transactionCapabilities = TransactionCapabilities(AtomicCapabilities())
            override suspend fun executeTransaction(request: TransactionRequest): TransactionResponse {
                if (request.requests.isEmpty()) return TransactionResponse(TransactionStatus.Staged, revision = Bytes(byteArrayOf(0)), version = 1uL)
                val add = assertIs<AddRequest<*>>(request.requests.single())
                val response = listOf(AddResponse(SimpleMarykModel, listOf(AddSuccess(SimpleMarykModel.key(add.keysForObjects!!.single().bytes), 2uL, emptyList()))))
                if (!request.commit) return TransactionResponse(TransactionStatus.Staged, response, version = 2uL)
                submitted++
                if (submitted == 1) throw IllegalStateException("Lost response after commit")
                return TransactionResponse(TransactionStatus.Committed, response, version = 2uL)
            }
        }
        val state = CliState().apply { replaceConnection(RocksDbStoreConnection("/test", store)) }
        val context = CommandContext(CommandRegistry(state, BasicCliEnvironment), state, BasicCliEnvironment)
        val command = SqlCommand()
        val statement = listOf("--write", "INSERT INTO SimpleMarykModel (value) VALUES ('haha-uncertain')")
        val result = command.execute(context, statement)
        assertTrue(result.isError)
        assertEquals("OutcomeUnknown: affected rows unconfirmed", result.lines.first())
        assertTrue(state.pendingSqlCommit != null)
        assertTrue(command.execute(context, statement).isError)
        assertEquals(1, submitted)
        val parsed = assertIs<CommandLineParser.ParseResult.Success>(CommandLineParser.parse("sql --resolve-commit"))
        val resolved = command.execute(context, parsed.tokens.drop(1))
        assertFalse(resolved.isError)
        assertTrue(resolved.lines.first().startsWith("Committed:"))
        assertEquals(null, state.pendingSqlCommit)
        assertEquals(2, submitted)
    }

}
