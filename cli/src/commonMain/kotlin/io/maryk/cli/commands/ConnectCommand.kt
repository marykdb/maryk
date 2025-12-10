package io.maryk.cli.commands

import io.maryk.cli.CliInteraction
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.FoundationDbStoreConnection
import io.maryk.cli.InteractionResult
import io.maryk.cli.OptionSelectorInteraction
import io.maryk.cli.OptionSelectorInteraction.Option
import io.maryk.cli.OptionSelectorInteraction.Selection
import io.maryk.cli.RocksDbStoreConnection
import io.maryk.cli.StoreType
import kotlinx.coroutines.runBlocking
import maryk.core.models.RootDataModel
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.model.readStoredModelDefinitionsFromDirectory
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.model.readStoredModelDefinitionsFromPath
import maryk.foundationdb.tuple.Tuple
import maryk.rocksdb.DBOptions
import maryk.rocksdb.Options

class ConnectCommand(
    private val rocksDbConnector: RocksDbConnector = DefaultRocksDbConnector,
    private val foundationDbConnector: FoundationDbConnector = DefaultFoundationDbConnector,
) : Command {
    override val name: String = "connect"
    override val description: String = "Connect to a Maryk store (RocksDB, FoundationDB)."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        context.state.currentConnection?.let { current ->
            val descriptor = when (current) {
                is RocksDbStoreConnection -> "${current.type.displayName} at ${current.directory}"
                else -> current.type.displayName
            }
            return CommandResult(
                lines = listOf(
                    "Already connected to $descriptor.",
                    "Use `disconnect` before running `connect` again.",
                ),
                isError = true,
            )
        }

        if (arguments.isEmpty()) {
            context.state.startInteraction(StoreSelectionInteraction(context))
            return CommandResult(
                lines = listOf(
                    "Interactive connect setup started.",
                    "Follow the prompts to choose a store and configure the connection.",
                    "Type `cancel` at any prompt to stop.",
                ),
            )
        }

        val storeToken = arguments.first().lowercase()
        val remaining = arguments.drop(1)

        return when (storeToken) {
            "rocksdb", "rocks" -> connectRocksDb(context, remaining)
            "foundationdb", "fdb" -> connectFoundationDb(context, remaining)
            else -> CommandResult(
                lines = buildList {
                    add("Unknown store type `$storeToken`.")
                    add("Supported stores: rocksdb (local embedded), foundationdb.")
                    add("Run `connect` without arguments for an interactive setup.")
                    add("Usage: connect rocksdb --dir <directory>")
                },
                isError = true,
            )
        }
    }

    private fun connectRocksDb(
        context: CommandContext,
        arguments: List<String>,
    ): CommandResult {
        return when (val parseResult = parseDirectory(arguments)) {
            is ParseDirectoryResult.Error -> CommandResult(
                lines = listOf(
                    "Store type: ${StoreType.ROCKS_DB.displayName}",
                    "Status: failed - ${parseResult.reason}",
                    "Provide a directory with `connect rocksdb --dir <directory>` or use interactive mode by typing `connect`.",
                ),
                isError = true,
            )
            is ParseDirectoryResult.Success -> when (val result = connectToRocksDb(context, parseResult.rawDirectory)) {
                is RocksDbConnectResult.Success -> CommandResult(lines = result.lines)
                is RocksDbConnectResult.Error -> CommandResult(lines = result.lines, isError = true)
            }
        }
    }

    private fun connectFoundationDb(
        context: CommandContext,
        arguments: List<String>,
    ): CommandResult {
        return when (val parseResult = parseFoundationOptions(arguments)) {
            is ParseFoundationOptionsResult.Error -> CommandResult(
                lines = listOf(
                    "Store type: ${StoreType.FOUNDATION_DB.displayName}",
                    "Status: failed - ${parseResult.reason}",
                    "Usage: connect foundationdb --dir <directory> [--cluster <cluster file>] [--tenant <tenant>]",
                ),
                isError = true,
            )
            is ParseFoundationOptionsResult.Success -> {
                val outcome = runCatching { connectToFoundationDb(context, parseResult.options) }
                    .onFailure { println("ConnectToFoundationDb threw ${it::class.simpleName}: ${it.message}") }
                    .getOrElse {
                        return CommandResult(
                            lines = listOf(
                                "Store type: ${StoreType.FOUNDATION_DB.displayName}",
                                "Directory: ${parseResult.options.directoryPath.joinToString("/")}",
                                "Status: failed - ${it.message ?: it::class.simpleName}",
                            ),
                            isError = true,
                        )
                    }
                when (outcome) {
                    is FoundationDbConnectResult.Success -> CommandResult(lines = outcome.lines)
                    is FoundationDbConnectResult.Error -> CommandResult(lines = outcome.lines, isError = true)
                }
            }
        }
    }

    private fun connectToRocksDb(
        context: CommandContext,
        rawDirectory: String,
    ): RocksDbConnectResult {
        return when (val resolution = context.environment.resolveDirectory(rawDirectory)) {
            is DirectoryResolution.Failure -> RocksDbConnectResult.Error(
                listOf(
                    "Store type: ${StoreType.ROCKS_DB.displayName}",
                    "Status: failed - ${resolution.message}",
                ),
            )
            is DirectoryResolution.Success -> {
                val normalized = resolution.normalizedPath
                when (val outcome = rocksDbConnector.connect(normalized)) {
                    is RocksDbConnectionOutcome.Success -> {
                        context.state.replaceConnection(outcome.connection)?.close()
                        RocksDbConnectResult.Success(
                            listOf(
                                "Store type: ${StoreType.ROCKS_DB.displayName}",
                                "Directory: $normalized",
                                "Status: connected",
                            ),
                        )
                    }
                    is RocksDbConnectionOutcome.Error -> RocksDbConnectResult.Error(
                        listOf(
                            "Store type: ${StoreType.ROCKS_DB.displayName}",
                            "Directory: $normalized",
                            "Status: failed - ${outcome.reason}",
                        ),
                    )
                }
            }
        }
    }

    private fun connectToFoundationDb(
        context: CommandContext,
        options: FoundationOptions,
    ): FoundationDbConnectResult {
        val normalizedPath = options.directoryPath

        return when (val outcome = foundationDbConnector.connect(options)) {
            is FoundationDbConnectionOutcome.Success -> {
                context.state.replaceConnection(outcome.connection)?.close()
                FoundationDbConnectResult.Success(
                    listOf(
                        "Store type: ${StoreType.FOUNDATION_DB.displayName}",
                        "Directory: ${normalizedPath.joinToString(separator = "/")}",
                        "Status: connected",
                    ),
                )
            }
            is FoundationDbConnectionOutcome.Error -> FoundationDbConnectResult.Error(
                listOf(
                    "Store type: ${StoreType.FOUNDATION_DB.displayName}",
                    "Directory: ${normalizedPath.joinToString(separator = "/")}",
                    "Status: failed - ${outcome.reason}",
                ),
            )
        }
    }

    private sealed class ParseDirectoryResult {
        data class Success(val rawDirectory: String) : ParseDirectoryResult()
        data class Error(val reason: String) : ParseDirectoryResult()
    }

    private sealed class ParseFoundationOptionsResult {
        data class Success(val options: FoundationOptions) : ParseFoundationOptionsResult()
        data class Error(val reason: String) : ParseFoundationOptionsResult()
    }

    private sealed class RocksDbConnectResult {
        data class Success(val lines: List<String>) : RocksDbConnectResult()
        data class Error(val lines: List<String>) : RocksDbConnectResult()
    }

    private sealed class FoundationDbConnectResult {
        data class Success(val lines: List<String>) : FoundationDbConnectResult()
        data class Error(val lines: List<String>) : FoundationDbConnectResult()
    }

    sealed class RocksDbConnectionOutcome {
        data class Success(val connection: RocksDbStoreConnection) : RocksDbConnectionOutcome()
        data class Error(val reason: String) : RocksDbConnectionOutcome()
    }

    sealed class FoundationDbConnectionOutcome {
        data class Success(val connection: FoundationDbStoreConnection) : FoundationDbConnectionOutcome()
        data class Error(val reason: String) : FoundationDbConnectionOutcome()
    }

    data class FoundationOptions(
        val directoryPath: List<String>,
        val clusterFile: String?,
        val tenant: String?,
    )

    private fun defaultClusterFile(): String = "store/foundationdb/fdb.cluster"

    private fun parseDirectory(arguments: List<String>): ParseDirectoryResult {
        if (arguments.isEmpty()) {
            return ParseDirectoryResult.Error("Directory is required.")
        }

        var directory: String? = null
        var index = 0
        while (index < arguments.size) {
            val token = arguments[index]
            when {
                token.startsWith("--dir=") -> {
                    if (directory != null) {
                        return ParseDirectoryResult.Error("Directory provided multiple times.")
                    }
                    directory = token.substringAfter("=", missingDelimiterValue = "")
                    if (directory.isEmpty()) {
                        return ParseDirectoryResult.Error("`--dir` requires a value.")
                    }
                }
                token == "--dir" -> {
                    if (directory != null) {
                        return ParseDirectoryResult.Error("Directory provided multiple times.")
                    }
                    if (index + 1 >= arguments.size) {
                        return ParseDirectoryResult.Error("`--dir` requires a value.")
                    }
                    directory = arguments[index + 1]
                    index += 1
                }
                token.startsWith("-") -> {
                    return ParseDirectoryResult.Error("Unknown option `$token`.")
                }
                directory == null -> {
                    directory = token
                }
                else -> {
                    return ParseDirectoryResult.Error("Unexpected argument `$token`.")
                }
            }
            index += 1
        }

        val finalDirectory = directory
        return if (finalDirectory == null) {
            ParseDirectoryResult.Error("Directory is required.")
        } else {
            ParseDirectoryResult.Success(finalDirectory)
        }
    }

    private fun parseFoundationOptions(arguments: List<String>): ParseFoundationOptionsResult {
        if (arguments.isEmpty()) {
            return ParseFoundationOptionsResult.Error("Directory is required.")
        }

        var directory: String? = null
        var cluster: String? = null
        var tenant: String? = null
        var index = 0
        while (index < arguments.size) {
            val token = arguments[index]
            when {
                token.startsWith("--dir=") -> {
                    if (directory != null) {
                        return ParseFoundationOptionsResult.Error("Directory provided multiple times.")
                    }
                    directory = token.substringAfter("=", missingDelimiterValue = "")
                    if (directory.isEmpty()) {
                        return ParseFoundationOptionsResult.Error("`--dir` requires a value.")
                    }
                }
                token == "--dir" -> {
                    if (directory != null) {
                        return ParseFoundationOptionsResult.Error("Directory provided multiple times.")
                    }
                    if (index + 1 >= arguments.size) {
                        return ParseFoundationOptionsResult.Error("`--dir` requires a value.")
                    }
                    directory = arguments[++index]
                }
                token.startsWith("--cluster=") -> {
                    if (cluster != null) {
                        return ParseFoundationOptionsResult.Error("Cluster file provided multiple times.")
                    }
                    cluster = token.substringAfter("=", missingDelimiterValue = "")
                }
                token == "--cluster" -> {
                    if (cluster != null) {
                        return ParseFoundationOptionsResult.Error("Cluster file provided multiple times.")
                    }
                    if (index + 1 >= arguments.size) {
                        return ParseFoundationOptionsResult.Error("`--cluster` requires a value.")
                    }
                    cluster = arguments[++index]
                }
                token.startsWith("--tenant=") -> {
                    if (tenant != null) {
                        return ParseFoundationOptionsResult.Error("Tenant provided multiple times.")
                    }
                    tenant = token.substringAfter("=", missingDelimiterValue = "")
                }
                token == "--tenant" -> {
                    if (tenant != null) {
                        return ParseFoundationOptionsResult.Error("Tenant provided multiple times.")
                    }
                    if (index + 1 >= arguments.size) {
                        return ParseFoundationOptionsResult.Error("`--tenant` requires a value.")
                    }
                    tenant = arguments[++index]
                }
                token.startsWith("-") -> {
                    return ParseFoundationOptionsResult.Error("Unknown option `$token`.")
                }
                directory == null -> directory = token
                else -> return ParseFoundationOptionsResult.Error("Unexpected argument `$token`.")
            }
            index += 1
        }

        val finalDirectory = directory
        return if (finalDirectory.isNullOrEmpty()) {
            ParseFoundationOptionsResult.Error("Directory is required.")
        } else {
            val dirParts = finalDirectory.split('/').filter { it.isNotEmpty() }
            val clusterFileResolved = cluster?.ifEmpty { null } ?: defaultClusterFile()
            ParseFoundationOptionsResult.Success(
                FoundationOptions(
                    directoryPath = dirParts.ifEmpty { listOf(finalDirectory) },
                    clusterFile = clusterFileResolved,
                    tenant = tenant?.ifEmpty { null },
                ),
            )
        }
    }

    private inner class StoreSelectionInteraction(
        private val context: CommandContext,
    ) : CliInteraction by OptionSelectorInteraction(
        options = listOf(
            Option(StoreType.ROCKS_DB, "RocksDB (embedded, local directory)"),
            Option(StoreType.FOUNDATION_DB, "FoundationDB (cluster file + directory path)"),
        ),
        promptLabel = "store> ",
        introLines = listOf("Select store type (use up/down arrows and press Enter):"),
        onSelection = { option ->
            when (option.value) {
                StoreType.ROCKS_DB -> InteractionResult.Continue(
                    next = RocksDbDirectoryInteraction(context),
                    lines = listOf("Selected RocksDB. Provide a directory path."),
                )
                StoreType.FOUNDATION_DB -> InteractionResult.Continue(
                    next = FoundationDbDirectoryInteraction(context),
                    lines = listOf("Selected FoundationDB. Provide a directory path (e.g. maryk/app/store)."),
                )
            }
        },
        onCancel = {
            InteractionResult.Complete(listOf("Connect command cancelled."))
        },
        resolveSelection = { input, currentIndex, _ ->
            val normalized = input.lowercase()
            when {
                normalized.isEmpty() -> Selection.Select(currentIndex)
                normalized in listOf("1", "rocksdb", "rocks", "r") -> Selection.Select(0)
                normalized in listOf("2", "foundationdb", "fdb") -> Selection.Select(1)
                normalized == "cancel" -> Selection.Cancel()
                else -> Selection.Error("Unrecognized choice `$input`. Use arrow keys or enter a number.")
            }
        },
    )

    private inner class RocksDbDirectoryInteraction(
        private val context: CommandContext,
    ) : CliInteraction {
        override val promptLabel: String = "dir> "
        override val introLines: List<String> = listOf(
            "Enter the RocksDB store directory path:",
            "Type `cancel` to abort.",
        )

        override fun onInput(input: String): InteractionResult {
            val trimmed = input.trim()
            if (trimmed.equals("cancel", ignoreCase = true)) {
                return InteractionResult.Complete(
                    listOf("Connect command cancelled."),
                )
            }
            if (trimmed.isEmpty()) {
                return InteractionResult.Stay(
                    listOf("Directory is required."),
                )
            }

            return when (val result = connectToRocksDb(context, trimmed)) {
                is RocksDbConnectResult.Success -> InteractionResult.Complete(result.lines)
                is RocksDbConnectResult.Error -> InteractionResult.Stay(result.lines)
            }
        }
    }

    private inner class FoundationDbDirectoryInteraction(
        private val context: CommandContext,
    ) : CliInteraction {
        override val promptLabel: String = "dir> "
        override val introLines: List<String> = listOf(
            "Enter the FoundationDB directory path (slash-separated).",
            "Optional: append cluster file with --cluster <path> or tenant with --tenant <name> after choosing connect command non-interactively.",
            "Type `cancel` to abort.",
        )

        override fun onInput(input: String): InteractionResult {
            val trimmed = input.trim()
            if (trimmed.equals("cancel", ignoreCase = true)) {
                return InteractionResult.Complete(listOf("Connect command cancelled."))
            }
            if (trimmed.isEmpty()) {
                return InteractionResult.Stay(listOf("Directory is required."))
            }

            return when (val parseResult = parseFoundationOptions(listOf(trimmed))) {
                is ParseFoundationOptionsResult.Error -> InteractionResult.Stay(listOf(parseResult.reason))
                is ParseFoundationOptionsResult.Success -> when (
                    val result = connectToFoundationDb(context, parseResult.options)
                ) {
                    is FoundationDbConnectResult.Success -> InteractionResult.Complete(result.lines)
                    is FoundationDbConnectResult.Error -> InteractionResult.Stay(result.lines)
                }
            }
        }
    }
}


fun interface RocksDbConnector {
    fun connect(path: String): ConnectCommand.RocksDbConnectionOutcome
}

object DefaultRocksDbConnector : RocksDbConnector {
    override fun connect(path: String): ConnectCommand.RocksDbConnectionOutcome {
        return try {
            val modelsById = loadStoredModels(path)
            val dataStore = runBlocking {
                RocksDBDataStore.open(
                    relativePath = path,
                    dataModelsById = modelsById,
                )
            }

            ConnectCommand.RocksDbConnectionOutcome.Success(
                RocksDbStoreConnection(
                    directory = path,
                    dataStore = dataStore,
                ),
            )
        } catch (e: Exception) {
            ConnectCommand.RocksDbConnectionOutcome.Error(
                reason = e.message ?: e::class.simpleName ?: "Unknown error",
            )
        }
    }

    private fun loadStoredModels(path: String): Map<UInt, RootDataModel<*>> =
        Options().use { listOptions ->
            DBOptions().use { dbOptions ->
                readStoredModelDefinitionsFromPath(path, listOptions, dbOptions)
            }
        }
}

fun interface FoundationDbConnector {
    fun connect(options: ConnectCommand.FoundationOptions): ConnectCommand.FoundationDbConnectionOutcome
}

object DefaultFoundationDbConnector : FoundationDbConnector {
    override fun connect(options: ConnectCommand.FoundationOptions): ConnectCommand.FoundationDbConnectionOutcome {
        return try {
            options.tenant

            val modelsById = runBlocking {
                readStoredModelDefinitionsFromDirectory(
                    fdbClusterFilePath = options.clusterFile,
                    directoryPath = options.directoryPath,
                    tenantName = options.tenant?.let { Tuple.from(it) },
                )
            }

            val effectiveModels = modelsById.ifEmpty {
                val dir = options.directoryPath.joinToString("/")
                val cluster = options.clusterFile ?: "default cluster"
                return ConnectCommand.FoundationDbConnectionOutcome.Error(
                    reason = "No stored models found at directory `$dir` on $cluster. Check the directory path (e.g., maryk/test/runner) or seed data first.",
                )
            }

            val dataStore = runBlocking {
                FoundationDBDataStore.open(
                    keepAllVersions = true,
                    fdbClusterFilePath = options.clusterFile,
                    directoryPath = options.directoryPath,
                    tenantName = options.tenant?.let { Tuple.from(it) },
                    dataModelsById = effectiveModels,
                )
            }

            ConnectCommand.FoundationDbConnectionOutcome.Success(
                FoundationDbStoreConnection(
                    directoryPath = options.directoryPath,
                    clusterFilePath = options.clusterFile,
                    tenantName = options.tenant,
                    dataStore = dataStore,
                ),
            )
        } catch (e: Exception) {
            ConnectCommand.FoundationDbConnectionOutcome.Error(
                reason = e.message ?: e::class.simpleName ?: "Unknown error",
            )
        } catch (t: Throwable) {
            val looksLikeMissingLib = t::class.simpleName == "UnsatisfiedLinkError" ||
                (t.message?.contains("fdb_c", ignoreCase = true) == true)
            val reason = if (looksLikeMissingLib) {
                "FoundationDB native client (libfdb_c) is missing. Install with store/foundationdb/scripts/install-foundationdb.sh or install FoundationDB system-wide."
            } else {
                t.message ?: t::class.simpleName ?: "Unknown error"
            }
            println("FDB connect debug (Throwable): ${t::class.simpleName}: ${t.message}")
            t.printStackTrace()
            ConnectCommand.FoundationDbConnectionOutcome.Error(
                reason = reason,
            )
        }
    }
}
