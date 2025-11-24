package io.maryk.cli.commands

class ListCommand : Command {
    override val name: String = "list"
    override val description: String = "List models stored in the connected datastore."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        val connection = context.state.currentConnection
            ?: return CommandResult(
                lines = listOf("Not connected to any store. Use `connect` first."),
                isError = true,
            )

        val modelsById = connection.dataStore.dataModelsById
        if (modelsById.isEmpty()) {
            return CommandResult(lines = listOf("No models found in the connected store."))
        }

        val maxDigits = modelsById.keys.maxOf { it.toString().length }

        val lines = buildList {
            add("Models:")
            modelsById.entries
                .sortedBy { it.key }
                .forEach { (id, model) ->
                    val paddedId = id.toString().padStart(maxDigits, ' ')
                    add("$paddedId - ${model.Meta.name}")
                }
        }

        return CommandResult(lines = lines)
    }
}
