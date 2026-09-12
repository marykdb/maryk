package io.maryk.cli.commands

import io.maryk.cli.BasicCliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.CommandLineParser
import io.maryk.cli.RocksDbStoreConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SqlCommandTest {
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
