package io.maryk.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandLineParserTest {
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
