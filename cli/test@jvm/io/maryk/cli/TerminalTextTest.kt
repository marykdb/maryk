package io.maryk.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalTextTest {
    @Test
    fun controlCharactersAreRenderedAsLiterals() {
        assertEquals(
            "orders\\n\\u001b[2J\\t\\u007f",
            "orders\n\u001B[2J\t\u007f".toTerminalText(),
        )
    }
}
