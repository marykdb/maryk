package io.maryk.cli.commands

import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.shared.IsDataStore
import maryk.yaml.YamlWriter

class ChangesCommand : Command {
    override val name: String = "changes"
    override val description: String = "Show versioned changes for a record."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        val connection = context.state.currentConnection
            ?: return CommandResult(
                lines = listOf("Not connected to any store. Use `connect` first."),
                isError = true,
            )

        if (arguments.size < 2) {
            return CommandResult(
                lines = listOf(
                    "Usage: changes <model> <base64-key> [--from-version <n>] [--to-version <n>] [--limit <n>] [--include-deleted]",
                    "Example: changes Client AbCdEf123 --from-version 10",
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
            is ChangesOptionsResult.Error -> return CommandResult(
                lines = listOf("Changes failed: ${parsed.message}"),
                isError = true,
            )
            is ChangesOptionsResult.Success -> parsed.options
        }

        val request = dataModel.getChanges(
            key,
            fromVersion = options.fromVersion,
            toVersion = options.toVersion,
            maxVersions = options.maxVersions,
            filterSoftDeleted = !options.includeDeleted,
        )

        val response: ChangesResponse<IsRootDataModel> = runBlocking { dataStore.execute(request) }

        val requestContext = RequestContext(
            DefinitionsContext(mutableMapOf(dataModel.Meta.name to DataModelReference(dataModel))),
            dataModel = dataModel,
        )
        val yaml = buildString {
            val writer = YamlWriter { append(it) }
            ChangesResponse.Serializer.writeObjectAsJson(response, writer, requestContext)
        }

        val yamlLines = sanitizeOutput(yaml)
            .trimEnd()
            .lineSequence()
            .map { line -> line.filter { it == '\t' || it >= ' ' } }
            .toList()

        val lines = buildList {
            add("Model: ${dataModel.Meta.name}")
            add("Key: $keyToken")
            add("From version: ${options.fromVersion}")
            add("To version: ${options.toVersion?.toString() ?: "<latest>"}")
            add("Max versions: ${options.maxVersions}")
            add("Include deleted: ${options.includeDeleted}")
            add("Lines: ${yamlLines.size}")
            add("----- Changes -----")
            if (yamlLines.isEmpty()) {
                add("<no changes>")
            } else {
                addAll(yamlLines)
            }
            add("----- End of changes: ${dataModel.Meta.name} $keyToken -----")
        }

        return CommandResult(lines = lines)
    }

    private data class ChangesOptions(
        val fromVersion: ULong,
        val toVersion: ULong?,
        val maxVersions: UInt,
        val includeDeleted: Boolean,
    )

    private sealed class ChangesOptionsResult {
        data class Success(val options: ChangesOptions) : ChangesOptionsResult()
        data class Error(val message: String) : ChangesOptionsResult()
    }

    private fun parseOptions(tokens: List<String>): ChangesOptionsResult {
        var fromVersion = 0uL
        var toVersion: ULong? = null
        var maxVersions = 1000u
        var includeDeleted = false
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            val lowered = token.lowercase()
            when {
                lowered == "--include-deleted" -> includeDeleted = true
                lowered.startsWith("--from-version=") -> {
                    val value = token.substringAfter("=", missingDelimiterValue = "").ifBlank {
                        return ChangesOptionsResult.Error("`--from-version` requires a value.")
                    }
                    fromVersion = value.toULongOrNull()
                        ?: return ChangesOptionsResult.Error("Invalid `--from-version` value: $value")
                }
                lowered == "--from-version" -> {
                    if (index + 1 >= tokens.size) {
                        return ChangesOptionsResult.Error("`--from-version` requires a value.")
                    }
                    val value = tokens[index + 1]
                    fromVersion = value.toULongOrNull()
                        ?: return ChangesOptionsResult.Error("Invalid `--from-version` value: $value")
                    index += 1
                }
                lowered.startsWith("--to-version=") -> {
                    val value = token.substringAfter("=", missingDelimiterValue = "").ifBlank {
                        return ChangesOptionsResult.Error("`--to-version` requires a value.")
                    }
                    toVersion = value.toULongOrNull()
                        ?: return ChangesOptionsResult.Error("Invalid `--to-version` value: $value")
                }
                lowered == "--to-version" -> {
                    if (index + 1 >= tokens.size) {
                        return ChangesOptionsResult.Error("`--to-version` requires a value.")
                    }
                    val value = tokens[index + 1]
                    toVersion = value.toULongOrNull()
                        ?: return ChangesOptionsResult.Error("Invalid `--to-version` value: $value")
                    index += 1
                }
                lowered.startsWith("--limit=") -> {
                    val value = token.substringAfter("=", missingDelimiterValue = "").ifBlank {
                        return ChangesOptionsResult.Error("`--limit` requires a value.")
                    }
                    maxVersions = value.toUIntOrNull()
                        ?: return ChangesOptionsResult.Error("Invalid `--limit` value: $value")
                }
                lowered == "--limit" -> {
                    if (index + 1 >= tokens.size) {
                        return ChangesOptionsResult.Error("`--limit` requires a value.")
                    }
                    val value = tokens[index + 1]
                    maxVersions = value.toUIntOrNull()
                        ?: return ChangesOptionsResult.Error("Invalid `--limit` value: $value")
                    index += 1
                }
                token.startsWith("--") -> return ChangesOptionsResult.Error("Unknown option: $token")
                else -> return ChangesOptionsResult.Error("Unexpected argument: $token")
            }
            index += 1
        }

        return ChangesOptionsResult.Success(
            ChangesOptions(
                fromVersion = fromVersion,
                toVersion = toVersion,
                maxVersions = maxVersions,
                includeDeleted = includeDeleted,
            )
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

    private fun sanitizeOutput(value: String): String {
        if (value.isEmpty()) return value
        val withoutAnsi = ANSI_ESCAPE.replace(value, "")
        return withoutAnsi.filter { char ->
            char == '\n' || char == '\t' || !char.isISOControl()
        }
    }

    private companion object {
        private val ANSI_ESCAPE = Regex(
            "\\u001B\\[[0-?]*[ -/]*[@-~]|\\u009B[0-?]*[ -/]*[@-~]"
        )
    }
}
