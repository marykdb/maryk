package io.maryk.cli.commands

import io.maryk.cli.BasicCliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.CommandLineParser
import io.maryk.cli.RocksDbStoreConnection
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
    fun explicitWritesPreserveSqlAndReportPartialFailures() {
        var calls = 0
        val store = object : FakeDataStore(mapOf(1u to SimpleMarykModel)) {
            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(request: RQ): RP {
                val add = assertIs<AddRequest<*>>(request)
                assertEquals("ha'quoted", add.objects.first().original(1u))
                val key = SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize))
                calls++
                return AddResponse(SimpleMarykModel, if (calls == 1) listOf(AddSuccess(key, 1u, emptyList())) else listOf(AlreadyExists(key))) as RP
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
        assertEquals(listOf("Affected rows: 1"), success.lines)
        val failure = SqlCommand().execute(context, listOf("--write", statement))
        assertTrue(failure.isError)
        assertEquals("Affected rows: 0", failure.lines.first())
        assertTrue(failure.lines.last().startsWith("Failed:"))
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
}
