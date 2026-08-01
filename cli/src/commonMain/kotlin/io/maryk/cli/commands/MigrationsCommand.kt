package io.maryk.cli.commands

import kotlinx.coroutines.runBlocking
import maryk.datastore.shared.migration.MigrationAdmin
import maryk.datastore.shared.rethrowIfFatal

class MigrationsCommand : Command {
    override val name = "migrations"
    override val description = "Inspect or control datastore migrations."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        val store = context.state.currentConnection?.dataStore
            ?: return error("Not connected to any store. Use `connect` first.")
        val admin = store as? MigrationAdmin
            ?: return error("The connected store does not expose migration administration.")

        val operation = arguments.firstOrNull()?.lowercase() ?: "status"
        if (operation == "status") {
            val snapshot = try {
                runBlocking { admin.getMigrationSnapshot() }
            } catch (error: Throwable) {
                error.rethrowIfFatal()
                return error("Migration status failed: ${error.message ?: error::class.simpleName}")
            }
            val statuses = snapshot.statuses
            val metrics = snapshot.metrics
            if (statuses.isEmpty() && metrics.isEmpty()) {
                return CommandResult(listOf("No active or recorded migrations."))
            }
            val ids = (statuses.keys + metrics.keys).sorted()
            return CommandResult(
                buildList {
                    add("Migrations:")
                    ids.forEach { id ->
                        val model = store.dataModelsById[id]?.Meta?.name ?: "model-$id"
                        val status = statuses[id]
                        val metric = metrics[id]
                        val detail = buildList {
                            status?.let {
                                add("state=${it.state}")
                                it.phase?.let { phase -> add("phase=$phase") }
                                it.attempt?.let { attempt -> add("attempt=$attempt") }
                                it.etaMs?.let { eta -> add("etaMs=$eta") }
                                it.message?.takeIf(String::isNotBlank)?.let { message -> add("message=$message") }
                            }
                            metric?.let {
                                add("completed=${it.completed}")
                                add("failed=${it.failed}")
                                add("retries=${it.retries}")
                                add("partials=${it.partials}")
                            }
                        }.joinToString(" ")
                        add("  $id $model${detail.takeIf(String::isNotBlank)?.let { " — $it" }.orEmpty()}")
                    }
                }
            )
        }

        if (operation !in setOf("pause", "resume", "cancel")) {
            return error("Usage: migrations [status|pause|resume|cancel] [model] [reason]")
        }
        val modelToken = arguments.getOrNull(1)
            ?: return error("Migration $operation requires a model name or id.")
        val modelId = modelToken.toUIntOrNull()
            ?: store.dataModelIdsByString[modelToken]
            ?: return error("Unknown model `$modelToken`.")

        val changed = try {
            runBlocking {
                when (operation) {
                    "pause" -> admin.requestMigrationPause(modelId)
                    "resume" -> admin.requestMigrationResume(modelId)
                    else -> admin.requestMigrationCancel(
                        modelId,
                        arguments.drop(2).joinToString(" ").ifBlank { "Canceled by operator" },
                    )
                }
            }
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            return error("Migration $operation failed: ${error.message ?: error::class.simpleName}")
        }
        return if (changed) {
            CommandResult(listOf("Migration $operation accepted for model $modelId."))
        } else {
            error("Migration $operation was not applicable for model $modelId.")
        }
    }

    private fun error(message: String) = CommandResult(listOf(message), isError = true)
}
