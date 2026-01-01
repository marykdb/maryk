package io.maryk.cli.commands

import io.maryk.cli.ScanQueryParser
import io.maryk.cli.ScanViewerInteraction
import io.maryk.cli.terminalHeight
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.datastore.shared.IsDataStore

class ScanCommand : Command {
    override val name: String = "scan"
    override val description: String = "Scan records and browse results."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        val connection = context.state.currentConnection
            ?: return CommandResult(
                lines = listOf("Not connected to any store. Use `connect` first."),
                isError = true,
            )

        if (arguments.isEmpty()) {
            return CommandResult(
                lines = listOf(
                    "Usage: scan <model> [options]",
                    "Options:",
                    "  --start-key <base64>        Start scanning from this key",
                    "  --limit <n>                 Page size per fetch (default 100)",
                    "  --include-start             Include the start key (default)",
                    "  --exclude-start             Exclude the start key",
                    "  --to-version <n>            Scan as of a version",
                    "  --include-deleted           Include soft-deleted records",
                    "  --where <filter>            Filter expression (Maryk YAML)",
                    "  --order <ref,...>           Order by references, prefix with - for desc",
                    "  --select <ref,...>          Fetch only these fields",
                    "  --show <ref,...>            Show these fields first",
                    "  --max-chars <n>             Max characters per row (default 160)",
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

        val options = parseOptions(arguments.drop(1))
        if (options.errorMessage != null) {
            return CommandResult(lines = listOf("Scan parse error: ${options.errorMessage}"), isError = true)
        }

        val requestContext = RequestContext(
            DefinitionsContext(mutableMapOf(dataModel.Meta.name to DataModelReference(dataModel))),
            dataModel = dataModel,
        )

        val startKey = options.startKey?.let { keyToken ->
            try {
                dataModel.key(keyToken)
            } catch (e: Throwable) {
                return CommandResult(
                    lines = listOf("Invalid start key: ${e.message ?: e::class.simpleName}"),
                    isError = true,
                )
            }
        }

        val where = options.where?.let {
            try {
                ScanQueryParser.parseFilter(dataModel, it)
            } catch (e: Throwable) {
                return CommandResult(
                    lines = listOf("Invalid filter: ${e.message ?: e::class.simpleName}"),
                    isError = true,
                )
            }
        }

        val order = try {
            ScanQueryParser.parseOrder(dataModel, options.orderTokens)
        } catch (e: Throwable) {
            return CommandResult(
                lines = listOf("Invalid order: ${e.message ?: e::class.simpleName}"),
                isError = true,
            )
        }

        val selectPaths = ScanQueryParser.parseReferencePaths(options.selectTokens)
        val displayPaths = ScanQueryParser.parseReferencePaths(options.showTokens)

        val selectGraph = try {
            ScanQueryParser.parseSelectGraph(dataModel, (selectPaths + displayPaths).distinct())
        } catch (e: Throwable) {
            return CommandResult(
                lines = listOf("Invalid select: ${e.message ?: e::class.simpleName}"),
                isError = true,
            )
        }

        val interaction = ScanViewerInteraction(
            dataModel = dataModel,
            dataStore = dataStore,
            requestContext = requestContext,
            startKey = startKey,
            includeStart = options.includeStart,
            toVersion = options.toVersion,
            filterSoftDeleted = options.filterSoftDeleted,
            where = where,
            order = order,
            selectPaths = selectPaths,
            displayPaths = displayPaths,
            selectGraph = selectGraph,
            pageSize = options.limit,
            maxLineChars = options.maxChars,
            terminalHeight = terminalHeight(),
        )

        return if (context.state.isOneShotMode) {
            CommandResult(lines = interaction.snapshotLines(includeHeader = true, includeFooter = false))
        } else {
            context.state.startInteraction(interaction)
            CommandResult(lines = emptyList())
        }
    }

    private data class ParsedOptions(
        val startKey: String? = null,
        val includeStart: Boolean = true,
        val toVersion: ULong? = null,
        val filterSoftDeleted: Boolean = true,
        val where: String? = null,
        val orderTokens: List<String> = emptyList(),
        val selectTokens: List<String> = emptyList(),
        val showTokens: List<String> = emptyList(),
        val limit: UInt = 100u,
        val maxChars: Int = 160,
        val errorMessage: String? = null,
    )

    private fun parseOptions(tokens: List<String>): ParsedOptions {
        var startKey: String? = null
        var includeStart = true
        var toVersion: ULong? = null
        var filterSoftDeleted = true
        var where: String? = null
        val orderTokens = mutableListOf<String>()
        val selectTokens = mutableListOf<String>()
        val showTokens = mutableListOf<String>()
        var limit = 100u
        var maxChars = 160

        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            when {
                token == "--include-start" -> includeStart = true
                token == "--exclude-start" -> includeStart = false
                token == "--include-deleted" -> filterSoftDeleted = false
                token == "--filter-soft-deleted" -> filterSoftDeleted = true
                token.startsWith("--start-key=") -> {
                    startKey = token.substringAfter("=").ifBlank {
                        return ParsedOptions(errorMessage = "`--start-key` requires a value.")
                    }
                }
                token == "--start-key" -> {
                    val value = tokens.getOrNull(index + 1)
                        ?: return ParsedOptions(errorMessage = "`--start-key` requires a value.")
                    startKey = value
                    index += 1
                }
                token.startsWith("--to-version=") -> {
                    val value = token.substringAfter("=").ifBlank {
                        return ParsedOptions(errorMessage = "`--to-version` requires a value.")
                    }
                    toVersion = value.toULongOrNull()
                        ?: return ParsedOptions(errorMessage = "Invalid `--to-version` value: $value")
                }
                token == "--to-version" -> {
                    val value = tokens.getOrNull(index + 1)
                        ?: return ParsedOptions(errorMessage = "`--to-version` requires a value.")
                    toVersion = value.toULongOrNull()
                        ?: return ParsedOptions(errorMessage = "Invalid `--to-version` value: $value")
                    index += 1
                }
                token.startsWith("--limit=") -> {
                    val value = token.substringAfter("=").ifBlank {
                        return ParsedOptions(errorMessage = "`--limit` requires a value.")
                    }
                    limit = value.toUIntOrNull()
                        ?: return ParsedOptions(errorMessage = "Invalid `--limit` value: $value")
                }
                token == "--limit" -> {
                    val value = tokens.getOrNull(index + 1)
                        ?: return ParsedOptions(errorMessage = "`--limit` requires a value.")
                    limit = value.toUIntOrNull()
                        ?: return ParsedOptions(errorMessage = "Invalid `--limit` value: $value")
                    index += 1
                }
                token.startsWith("--max-chars=") -> {
                    val value = token.substringAfter("=").ifBlank {
                        return ParsedOptions(errorMessage = "`--max-chars` requires a value.")
                    }
                    maxChars = value.toIntOrNull()
                        ?: return ParsedOptions(errorMessage = "Invalid `--max-chars` value: $value")
                }
                token == "--max-chars" -> {
                    val value = tokens.getOrNull(index + 1)
                        ?: return ParsedOptions(errorMessage = "`--max-chars` requires a value.")
                    maxChars = value.toIntOrNull()
                        ?: return ParsedOptions(errorMessage = "Invalid `--max-chars` value: $value")
                    index += 1
                }
                token.startsWith("--where=") -> {
                    where = token.substringAfter("=").ifBlank {
                        return ParsedOptions(errorMessage = "`--where` requires a value.")
                    }
                }
                token == "--where" -> {
                    val (values, newIndex) = readOptionSpan(tokens, index + 1)
                        ?: return ParsedOptions(errorMessage = "`--where` requires a value.")
                    where = values.joinToString(" ")
                    index = newIndex
                }
                token.startsWith("--order=") -> {
                    orderTokens += token.substringAfter("=").ifBlank {
                        return ParsedOptions(errorMessage = "`--order` requires a value.")
                    }
                }
                token == "--order" -> {
                    val (values, newIndex) = readOptionSpan(tokens, index + 1)
                        ?: return ParsedOptions(errorMessage = "`--order` requires a value.")
                    orderTokens += values
                    index = newIndex
                }
                token.startsWith("--select=") -> {
                    selectTokens += token.substringAfter("=").ifBlank {
                        return ParsedOptions(errorMessage = "`--select` requires a value.")
                    }
                }
                token == "--select" -> {
                    val (values, newIndex) = readOptionSpan(tokens, index + 1)
                        ?: return ParsedOptions(errorMessage = "`--select` requires a value.")
                    selectTokens += values
                    index = newIndex
                }
                token.startsWith("--show=") -> {
                    showTokens += token.substringAfter("=").ifBlank {
                        return ParsedOptions(errorMessage = "`--show` requires a value.")
                    }
                }
                token == "--show" -> {
                    val (values, newIndex) = readOptionSpan(tokens, index + 1)
                        ?: return ParsedOptions(errorMessage = "`--show` requires a value.")
                    showTokens += values
                    index = newIndex
                }
                token.startsWith("--") -> {
                    return ParsedOptions(errorMessage = "Unknown option: $token")
                }
                else -> return ParsedOptions(errorMessage = "Unexpected argument: $token")
            }
            index += 1
        }

        if (limit == 0u) {
            return ParsedOptions(errorMessage = "`--limit` must be greater than 0.")
        }
        if (maxChars < 20) {
            return ParsedOptions(errorMessage = "`--max-chars` must be at least 20.")
        }

        return ParsedOptions(
            startKey = startKey,
            includeStart = includeStart,
            toVersion = toVersion,
            filterSoftDeleted = filterSoftDeleted,
            where = where,
            orderTokens = orderTokens,
            selectTokens = selectTokens,
            showTokens = showTokens,
            limit = limit,
            maxChars = maxChars,
        )
    }

    private fun readOptionSpan(tokens: List<String>, startIndex: Int): Pair<List<String>, Int>? {
        if (startIndex >= tokens.size) return null
        val values = mutableListOf<String>()
        var index = startIndex
        while (index < tokens.size && !tokens[index].startsWith("--")) {
            values.add(tokens[index])
            index += 1
        }
        if (values.isEmpty()) return null
        return values to (index - 1)
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
