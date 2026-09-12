package io.maryk.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandLineParserTest {
    @Test
    fun preservesSqlLiteralsQuotedNamesAndCommentsVerbatim() {
        val sql = "SELECT 'it''s  okay', \"Mixed Case\" FROM items -- keep this\nWHERE note = 'a\\b'"
        val result = assertIs<CommandLineParser.ParseResult.Success>(CommandLineParser.parse("sql $sql"))
        assertEquals(listOf("sql", sql), result.tokens)
        assertEquals(listOf("sql", sql), assertIs<CommandLineParser.ParseResult.Success>(CommandLineParser.parse("SQL $sql")).tokens)
    }

    @Test
    fun sqlOptionsPreserveTheRemainingStatementAndQuotedFilePath() {
        assertEquals(listOf("sql", "--snapshot", "SELECT 'a b'"), assertIs<CommandLineParser.ParseResult.Success>(CommandLineParser.parse("sql --snapshot SELECT 'a b'")).tokens)
        assertEquals(listOf("sql", "--file", "/tmp/a b.sql"), assertIs<CommandLineParser.ParseResult.Success>(CommandLineParser.parse("sql --file '/tmp/a b.sql'")).tokens)
    }

    @Test
    fun preservesExplicitEmptyQuotedArguments() {
        val result = assertIs<CommandLineParser.ParseResult.Success>(
            CommandLineParser.parse("set value \"\" ''")
        )

        assertEquals(listOf("set", "value", "", ""), result.tokens)
    }

    @Test
    fun preservesWindowsPathBackslashes() {
        val result = assertIs<CommandLineParser.ParseResult.Success>(
            CommandLineParser.parse("connect rocksdb --dir C:\\maryk\\store")
        )

        assertEquals(listOf("connect", "rocksdb", "--dir", "C:\\maryk\\store"), result.tokens)
    }

    @Test
    fun unescapesQuotedQuoteOnly() {
        val result = assertIs<CommandLineParser.ParseResult.Success>(
            CommandLineParser.parse("set value \"say \\\"hello\\\"\"")
        )

        assertEquals(listOf("set", "value", "say \"hello\""), result.tokens)
    }
}
