package io.maryk.cli.commands

/**
 * Lists the available commands and their descriptions.
 */
class HelpCommand : Command {
    override val name: String = "help"
    override val description: String = "Show available commands."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        val commands = context.registry.list()
            .sortedBy { it.name }

        val longestName = commands.maxOfOrNull { it.name.length } ?: 4
        val lines = buildList {
            add("Available commands:")
            commands.forEach { command ->
                val paddedName = command.name.padEnd(longestName)
                add("  $paddedName  ${command.description}")
            }
        }

        return CommandResult(lines = lines)
    }
}
