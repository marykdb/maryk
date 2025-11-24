package io.maryk.cli.commands

import io.maryk.cli.RocksDbStoreConnection
import io.maryk.cli.StoreConnection

class DisconnectCommand : Command {
    override val name: String = "disconnect"
    override val description: String = "Disconnect from the current store."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        val connection = context.state.currentConnection
            ?: return CommandResult(
                lines = listOf("No active store connection to disconnect."),
                isError = true,
            )

        context.state.clearConnection()

        return try {
            connection.close()
            CommandResult(
                lines = listOf("Disconnected from ${describe(connection)}."),
            )
        } catch (e: Exception) {
            CommandResult(
                lines = listOf(
                    "Disconnected from ${describe(connection)} with errors: ${e.message ?: e::class.simpleName}",
                ),
                isError = true,
            )
        }
    }

    private fun describe(connection: StoreConnection): String = when (connection) {
        is RocksDbStoreConnection -> "${connection.type.displayName} at ${connection.directory}"
        else -> connection.type.displayName
    }
}
