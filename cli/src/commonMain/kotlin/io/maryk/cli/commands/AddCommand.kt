package io.maryk.cli.commands

import io.maryk.cli.SaveFormat
import io.maryk.cli.readRecordValues
import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.requests.add
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.IsDataStore

class AddCommand : Command {
    override val name: String = "add"
    override val description: String = "Add a new record from a file."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        val connection = context.state.currentConnection
            ?: return CommandResult(
                lines = listOf("Not connected to any store. Use `connect` first."),
                isError = true,
            )

        if (arguments.size < 2) {
            return CommandResult(
                lines = listOf(
                    "Usage: add <model> <file> [--yaml|--json|--proto] [--meta] [--key <base64>]",
                    "Example: add SimpleMarykModel ./record.yaml",
                ),
                isError = true,
            )
        }

        val dataStore = connection.dataStore
        val modelToken = arguments[0]

        val dataModel = resolveDataModel(dataStore, modelToken)
            ?: return CommandResult(
                lines = listOf(
                    "Unknown model `$modelToken`.",
                    "Run `list` to see available models.",
                ),
                isError = true,
            )

        val optionsResult = parseAddOptions(arguments.drop(1))
        val options = when (optionsResult) {
            is AddOptionsResult.Error -> return CommandResult(
                lines = listOf("Add failed: ${optionsResult.message}"),
                isError = true,
            )
            is AddOptionsResult.Success -> optionsResult.options
        }

        val loaded = try {
            readRecordValues(dataModel, options.path, options.format, options.useMeta)
        } catch (e: Throwable) {
            return CommandResult(
                lines = listOf("Add failed: ${e.message ?: e::class.simpleName}"),
                isError = true,
            )
        }

        val explicitKey = options.keyToken?.let { token ->
            try {
                dataModel.key(token)
            } catch (e: Throwable) {
                return CommandResult(
                    lines = listOf("Add failed: invalid key: ${e.message ?: e::class.simpleName}"),
                    isError = true,
                )
            }
        }

        val metaKey = loaded.meta?.key
        if (explicitKey != null && metaKey != null && explicitKey != metaKey) {
            return CommandResult(
                lines = listOf("Add failed: `--key` does not match metadata key."),
                isError = true,
            )
        }

        val key = explicitKey ?: metaKey
        val request = if (key != null) {
            dataModel.add(key to loaded.values)
        } else {
            dataModel.add(loaded.values)
        }

        val response: AddResponse<IsRootDataModel> = runBlocking { dataStore.execute(request) }
        val formatted = formatStatuses(dataModel, response.statuses)

        return CommandResult(
            lines = formatted.lines,
            isError = formatted.isError,
        )
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

    private data class AddOptions(
        val path: String,
        val format: SaveFormat,
        val useMeta: Boolean,
        val keyToken: String?,
    )

    private sealed class AddOptionsResult {
        data class Success(val options: AddOptions) : AddOptionsResult()
        data class Error(val message: String) : AddOptionsResult()
    }

    private fun parseAddOptions(tokens: List<String>): AddOptionsResult {
        var path: String? = null
        var format: SaveFormat? = null
        var useMeta = false
        var keyToken: String? = null
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            val lowered = token.lowercase()
            when {
                lowered == "--yaml" -> {
                    val next = selectFormat(format, SaveFormat.YAML) ?: return AddOptionsResult.Error(
                        "Choose only one format: --yaml, --json, or --proto"
                    )
                    format = next
                }
                lowered == "--json" -> {
                    val next = selectFormat(format, SaveFormat.JSON) ?: return AddOptionsResult.Error(
                        "Choose only one format: --yaml, --json, or --proto"
                    )
                    format = next
                }
                lowered == "--proto" -> {
                    val next = selectFormat(format, SaveFormat.PROTO) ?: return AddOptionsResult.Error(
                        "Choose only one format: --yaml, --json, or --proto"
                    )
                    format = next
                }
                lowered == "--meta" -> useMeta = true
                lowered.startsWith("--key=") -> {
                    val value = token.substringAfter("=", missingDelimiterValue = "").ifBlank {
                        return AddOptionsResult.Error("`--key` requires a value.")
                    }
                    keyToken = value
                }
                lowered == "--key" -> {
                    if (index + 1 >= tokens.size) {
                        return AddOptionsResult.Error("`--key` requires a value.")
                    }
                    keyToken = tokens[index + 1]
                    index += 1
                }
                lowered == "--kotlin" -> {
                    return AddOptionsResult.Error("Kotlin input is not supported.")
                }
                token.startsWith("--") -> {
                    return AddOptionsResult.Error("Unknown option: $token")
                }
                path == null -> path = token
                else -> return AddOptionsResult.Error("Unexpected argument: $token")
            }
            index += 1
        }

        val resolvedPath = path ?: return AddOptionsResult.Error("Missing file path.")
        val resolvedFormat = format ?: SaveFormat.YAML

        return AddOptionsResult.Success(
            AddOptions(
                path = resolvedPath,
                format = resolvedFormat,
                useMeta = useMeta,
                keyToken = keyToken,
            )
        )
    }

    private fun selectFormat(current: SaveFormat?, next: SaveFormat): SaveFormat? =
        if (current == null || current == next) next else null

    private data class FormattedStatuses(
        val lines: List<String>,
        val isError: Boolean,
    )

    private fun formatStatuses(
        dataModel: IsRootDataModel,
        statuses: List<IsAddResponseStatus<IsRootDataModel>>,
    ): FormattedStatuses {
        if (statuses.isEmpty()) {
            return FormattedStatuses(
                lines = listOf("Add failed: no response status for ${dataModel.Meta.name}."),
                isError = true,
            )
        }

        var hasError = false
        val lines = statuses.map { status ->
            when (status) {
                is AddSuccess -> "Added ${dataModel.Meta.name} ${status.key} (version ${status.version})."
                is AlreadyExists -> {
                    hasError = true
                    "Add failed: ${dataModel.Meta.name} ${status.key} already exists."
                }
                is ValidationFail -> {
                    hasError = true
                    val details = status.exceptions.joinToString("; ") { exception ->
                        exception.message ?: exception.toString()
                    }
                    "Add failed: $details"
                }
                is AuthFail -> {
                    hasError = true
                    "Add failed: unauthorized."
                }
                is ServerFail -> {
                    hasError = true
                    "Add failed: ${status.reason}"
                }
                else -> {
                    hasError = true
                    "Add failed: ${status.statusType}"
                }
            }
        }

        return FormattedStatuses(lines = lines, isError = hasError)
    }
}
