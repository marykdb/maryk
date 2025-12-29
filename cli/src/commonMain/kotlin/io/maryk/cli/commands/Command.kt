package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DeleteContext
import io.maryk.cli.LoadContext
import io.maryk.cli.SaveContext

/**
 * Represents a single CLI command.
 */
interface Command {
    val name: String
    val description: String

    fun execute(context: CommandContext, arguments: List<String>): CommandResult
}

/**
 * Shared context for commands. Keeps access to other commands to enable help-style lookups.
 */
class CommandContext internal constructor(
    internal val registry: CommandRegistry,
    val state: CliState,
    val environment: CliEnvironment,
)

/**
 * Standardized command execution result so the UI can handle output consistently.
 */
data class CommandResult(
    val lines: List<String>,
    val isError: Boolean = false,
    val shouldExit: Boolean = false,
    val saveContext: SaveContext? = null,
    val deleteContext: DeleteContext? = null,
    val loadContext: LoadContext? = null,
)
