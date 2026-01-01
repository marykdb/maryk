package io.maryk.cli.commands

import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.IsDataStore

class UndeleteCommand : Command {
    override val name: String = "undelete"
    override val description: String = "Restore a soft-deleted record."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        val connection = context.state.currentConnection
            ?: return CommandResult(
                lines = listOf("Not connected to any store. Use `connect` first."),
                isError = true,
            )

        if (arguments.size < 2) {
            return CommandResult(
                lines = listOf(
                    "Usage: undelete <model> <base64-key> [--if-version <n>]",
                    "Example: undelete Client AbCdEf123",
                ),
                isError = true,
            )
        }

        val dataStore = connection.dataStore
        val modelToken = arguments[0]
        val keyToken = arguments[1]

        val dataModel = resolveDataModel(dataStore, modelToken)
            ?: return CommandResult(
                lines = listOf(
                    "Unknown model `$modelToken`.",
                    "Run `list` to see available models.",
                ),
                isError = true,
            )

        val key = try {
            dataModel.key(keyToken)
        } catch (e: Throwable) {
            return CommandResult(
                lines = listOf("Invalid key: ${e.message ?: e::class.simpleName}"),
                isError = true,
            )
        }

        val options = when (val parsed = parseOptions(arguments.drop(2))) {
            is UndeleteOptionsResult.Error -> return CommandResult(
                lines = listOf("Undelete failed: ${parsed.message}"),
                isError = true,
            )
            is UndeleteOptionsResult.Success -> parsed.options
        }

        val request = dataModel.change(
            key.change(ObjectSoftDeleteChange(false), lastVersion = options.ifVersion)
        )

        val response: ChangeResponse<IsRootDataModel> = runBlocking { dataStore.execute(request) }
        val status = response.statuses.firstOrNull()
            ?: return CommandResult(
                lines = listOf("Undelete failed: no response status for ${dataModel.Meta.name}."),
                isError = true,
            )

        val line = when (status) {
            is ChangeSuccess -> "Restored ${dataModel.Meta.name} $keyToken (version ${status.version})."
            is DoesNotExist -> "Undelete failed: ${dataModel.Meta.name} $keyToken does not exist."
            is ValidationFail -> {
                val details = status.exceptions.joinToString("; ") { exception ->
                    exception.message ?: exception.toString()
                }
                "Undelete failed: $details"
            }
            is ServerFail -> "Undelete failed: ${status.reason}"
            else -> "Undelete failed: ${status.statusType}"
        }

        return CommandResult(lines = listOf(line), isError = status !is ChangeSuccess)
    }

    private data class UndeleteOptions(
        val ifVersion: ULong?,
    )

    private sealed class UndeleteOptionsResult {
        data class Success(val options: UndeleteOptions) : UndeleteOptionsResult()
        data class Error(val message: String) : UndeleteOptionsResult()
    }

    private fun parseOptions(tokens: List<String>): UndeleteOptionsResult {
        var ifVersion: ULong? = null
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            val lowered = token.lowercase()
            when {
                lowered.startsWith("--if-version=") -> {
                    val value = token.substringAfter("=", missingDelimiterValue = "").ifBlank {
                        return UndeleteOptionsResult.Error("`--if-version` requires a value.")
                    }
                    ifVersion = value.toULongOrNull()
                        ?: return UndeleteOptionsResult.Error("Invalid `--if-version` value: $value")
                }
                lowered == "--if-version" -> {
                    if (index + 1 >= tokens.size) {
                        return UndeleteOptionsResult.Error("`--if-version` requires a value.")
                    }
                    val value = tokens[index + 1]
                    ifVersion = value.toULongOrNull()
                        ?: return UndeleteOptionsResult.Error("Invalid `--if-version` value: $value")
                    index += 1
                }
                token.startsWith("--") -> return UndeleteOptionsResult.Error("Unknown option: $token")
                else -> return UndeleteOptionsResult.Error("Unexpected argument: $token")
            }
            index += 1
        }

        return UndeleteOptionsResult.Success(UndeleteOptions(ifVersion))
    }

    private fun resolveDataModel(
        dataModelHolder: IsDataStore,
        token: String,
    ): IsRootDataModel? {
        val byName = dataModelHolder.dataModelIdsByString[token]?.let { dataModelHolder.dataModelsById[it] }
        if (byName != null) {
            return byName
        }

        val numericId = token.toUIntOrNull()
        return numericId?.let { dataModelHolder.dataModelsById[it] }
    }
}
