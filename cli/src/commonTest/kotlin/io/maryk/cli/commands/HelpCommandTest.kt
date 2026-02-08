package io.maryk.cli.commands

import io.maryk.cli.BasicCliEnvironment
import io.maryk.cli.CliState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelpCommandTest {
    @Test
    fun listsCommandsAlphabetically() {
        val registry = CommandRegistry(CliState(), BasicCliEnvironment)
            .register(
                object : Command {
                    override val name: String = "connect"
                    override val description: String = "Connect to a Maryk store."

                    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
                        return CommandResult(lines = listOf("Connecting..."))
                    }
                },
            )
            .register(HelpCommand())

        val result = registry.execute("help", emptyList())

        assertTrue(result.lines.first().startsWith("Available commands"))
        assertEquals(
            listOf(
                "  connect  Connect to a Maryk store.",
                "  help     Show available commands.",
                "Use `help <command>` for details.",
            ),
            result.lines.drop(1),
        )
    }

    @Test
    fun returnsErrorForUnknownCommand() {
        val registry = CommandRegistry(CliState(), BasicCliEnvironment)
            .register(HelpCommand())

        val result = registry.execute("missing", emptyList())

        assertTrue(result.isError)
        assertEquals(
            listOf(
                "Unknown command `missing`.",
                "Type `help` to see the list of available commands.",
            ),
            result.lines,
        )
    }

    @Test
    fun returnsDetailedHelpForServe() {
        val registry = CommandRegistry(CliState(), BasicCliEnvironment)
            .register(HelpCommand())
            .register(
                object : Command {
                    override val name: String = "serve"
                    override val description: String = "Serve store."

                    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
                        return CommandResult(lines = emptyList())
                    }
                },
            )

        val result = registry.execute("help", listOf("serve"))

        assertFalse(result.isError)
        assertEquals("serve <store> [options]", result.lines.first())
        assertTrue(result.lines.any { it.contains("serve --config <file>") })
    }
}
