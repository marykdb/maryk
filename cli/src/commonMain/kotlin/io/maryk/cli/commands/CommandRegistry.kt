package io.maryk.cli.commands

/**
 * Central registry for available CLI commands.
 */
class CommandRegistry {
    private val commands = linkedMapOf<String, Command>()

    fun register(command: Command): CommandRegistry {
        commands[command.name] = command
        return this
    }

    fun list(): List<Command> = commands.values.toList()

    fun execute(commandName: String, arguments: List<String>): CommandResult {
        val command = commands[commandName]
        return if (command != null) {
            val context = CommandContext(this)
            command.execute(context, arguments)
        } else {
            CommandResult(
                lines = listOf(
                    "Unknown command `$commandName`.",
                    "Type `help` to see the list of available commands.",
                ),
                isError = true,
            )
        }
    }
}

fun CommandRegistry.registerAll(vararg commands: Command): CommandRegistry = apply {
    commands.forEach { register(it) }
}
