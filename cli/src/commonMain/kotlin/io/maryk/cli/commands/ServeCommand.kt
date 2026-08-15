package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.StoreType
import io.maryk.cli.readEnvironmentVariable
import io.maryk.cli.readStdinText
import maryk.datastore.remote.RemoteStoreServer
import maryk.datastore.remote.RemoteStoreServerConfig
import maryk.datastore.remote.validateRemoteStoreServerBinding
import maryk.datastore.shared.rethrowIfFatal
import maryk.file.File

class ServeCommand(
    private val rocksDbConnector: RocksDbConnector = DefaultRocksDbConnector,
    private val foundationDbConnector: FoundationDbConnector = DefaultFoundationDbConnector,
    private val serverStarter: (RemoteStoreServer, String, Int, Boolean, String?) -> Unit = { server, host, port, allowInsecureRemoteBinding, bearerToken ->
        server.start(
            host,
            port,
            wait = true,
            config = RemoteStoreServerConfig(
                allowInsecureRemoteBinding = allowInsecureRemoteBinding,
                bearerToken = bearerToken,
            ),
        )
    },
) : Command {
    override val name: String = "serve"
    override val description: String = "Serve a Maryk store over HTTP using a small Ktor service."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        val parseResult = parseServeOptions(context.environment, arguments)
        val options = when (parseResult) {
            is ServeParseResult.Error -> return CommandResult(
                lines = listOf(
                    "Serve configuration error: ${parseResult.reason}",
                    "Usage: serve rocksdb --dir <path> [--host <host>] [--port <port>] [--bearer-token-env <name>|--bearer-token-file <path>|--bearer-token-stdin]",
                    "       serve foundationdb --dir <dir> [--cluster <file>] [--host <host>] [--port <port>] [--bearer-token-env <name>|--bearer-token-file <path>|--bearer-token-stdin]",
                    "       serve --config <file>",
                ),
                isError = true,
            )
            is ServeParseResult.Success -> parseResult.options
        }

        val connection = when (options.store) {
            is ServeStore.RocksDb -> when (val outcome = rocksDbConnector.connect(options.store.directory)) {
                is ConnectCommand.RocksDbConnectionOutcome.Success -> outcome.connection
                is ConnectCommand.RocksDbConnectionOutcome.Error -> return CommandResult(
                    lines = listOf("Failed to open RocksDB store: ${outcome.reason}"),
                    isError = true,
                )
            }
            is ServeStore.FoundationDb -> when (val outcome = foundationDbConnector.connect(options.store.options)) {
                is ConnectCommand.FoundationDbConnectionOutcome.Success -> outcome.connection
                is ConnectCommand.FoundationDbConnectionOutcome.Error -> return CommandResult(
                    lines = listOf("Failed to open FoundationDB store: ${outcome.reason}"),
                    isError = true,
                )
            }
        }

        val dataStore = connection.dataStore
        val server = RemoteStoreServer(dataStore)

        println("Serving ${options.store.displayName()} at http://${options.host}:${options.port}")
        if (options.warnBearerTokenInArguments) {
            println("Warning: --bearer-token exposes its secret in command input and, in one-shot use, process arguments; use --bearer-token-env, --bearer-token-file, or --bearer-token-stdin.")
        }
        when {
            options.bearerToken != null -> println("Bearer authentication enabled; use TLS or SSH to protect traffic in transit.")
            options.allowInsecureRemoteBinding -> println("Warning: explicitly allowing an unauthenticated remote bind without TLS.")
            else -> println("Loopback-only service; use SSH tunneling for remote access.")
        }
        println("Press Ctrl+C to stop.")

        return try {
            try {
                serverStarter(server, options.host, options.port, options.allowInsecureRemoteBinding, options.bearerToken)
                CommandResult(lines = listOf("Server stopped."))
            } catch (e: Throwable) {
                e.rethrowIfFatal()
                CommandResult(
                    lines = listOf("Serve failed: ${e.message ?: e::class.simpleName}"),
                    isError = true,
                )
            }
        } finally {
            connection.close()
        }
    }
}

internal sealed class ServeParseResult {
    data class Success(val options: ServeOptions) : ServeParseResult()
    data class Error(val reason: String) : ServeParseResult()
}

internal data class ServeOptions(
    val store: ServeStore,
    val host: String,
    val port: Int,
    val bearerToken: String?,
    val warnBearerTokenInArguments: Boolean,
    val allowInsecureRemoteBinding: Boolean,
)

internal sealed class BearerTokenSource {
    data class Literal(val token: String) : BearerTokenSource()
    data class Environment(val variable: String) : BearerTokenSource()
    data class File(val path: String) : BearerTokenSource()
    data object Stdin : BearerTokenSource()
}

internal sealed class ServeStore {
    data class RocksDb(val directory: String) : ServeStore()
    data class FoundationDb(val options: ConnectCommand.FoundationOptions) : ServeStore()

    fun displayName(): String = when (this) {
        is RocksDb -> StoreType.ROCKS_DB.displayName
        is FoundationDb -> StoreType.FOUNDATION_DB.displayName
    }
}

internal data class ServeConfigInput(
    val storeType: StoreType? = null,
    val directory: String? = null,
    val clusterFile: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val bearerTokenSource: BearerTokenSource? = null,
    val allowInsecureRemoteBinding: Boolean? = null,
) {
    fun merge(override: ServeConfigInput): ServeConfigInput = ServeConfigInput(
        storeType = override.storeType ?: storeType,
        directory = override.directory ?: directory,
        clusterFile = override.clusterFile ?: clusterFile,
        host = override.host ?: host,
        port = override.port ?: port,
        bearerTokenSource = override.bearerTokenSource ?: bearerTokenSource,
        allowInsecureRemoteBinding = override.allowInsecureRemoteBinding ?: allowInsecureRemoteBinding,
    )
}

private const val defaultHost = "127.0.0.1"
private const val defaultPort = 8210

internal fun parseServeOptions(environment: CliEnvironment, arguments: List<String>): ServeParseResult {
    val cliInput = when (val parsed = parseServeArguments(arguments)) {
        is CliParseResult.Error -> return ServeParseResult.Error(parsed.reason)
        is CliParseResult.Success -> parsed.input
    }

    val fileInput = if (cliInput.configPath != null) {
        val contents = File.readText(cliInput.configPath)
            ?: return ServeParseResult.Error("Config file not found at ${cliInput.configPath}")
        when (val parsed = parseServeConfig(contents)) {
            is ConfigParseResult.Error -> return ServeParseResult.Error(parsed.reason)
            is ConfigParseResult.Success -> parsed.input
        }
    } else {
        ServeConfigInput()
    }

    val merged = fileInput.merge(cliInput.input)
    val storeType = merged.storeType ?: return ServeParseResult.Error("Store type is required.")
    val directory = merged.directory ?: return ServeParseResult.Error("Store directory is required.")
    val host = merged.host?.ifBlank { defaultHost } ?: defaultHost
    val port = merged.port ?: defaultPort
    if (port !in 1..65535) return ServeParseResult.Error("Port must be between 1 and 65535.")
    val bearerToken = when (val source = merged.bearerTokenSource) {
        null -> null
        is BearerTokenSource.Literal -> source.token
        is BearerTokenSource.Environment -> readEnvironmentVariable(source.variable)
            ?: return ServeParseResult.Error("Bearer token environment variable `${source.variable}` is not set.")
        is BearerTokenSource.File -> File.readText(source.path)
            ?: return ServeParseResult.Error("Bearer token file not found at ${source.path}")
        BearerTokenSource.Stdin -> readStdinText()
    }?.trimEnd('\r', '\n')
    if (bearerToken != null && bearerToken.isBlank()) {
        return ServeParseResult.Error("Bearer token cannot be blank.")
    }
    try {
        validateRemoteStoreServerBinding(
            host,
            RemoteStoreServerConfig(
                allowInsecureRemoteBinding = merged.allowInsecureRemoteBinding ?: false,
                bearerToken = bearerToken,
            ),
        )
    } catch (error: IllegalArgumentException) {
        return ServeParseResult.Error(error.message ?: "Invalid Remote Store binding configuration.")
    }

    return when (storeType) {
        StoreType.ROCKS_DB -> when (val resolution = environment.resolveDirectory(directory)) {
            is DirectoryResolution.Failure -> ServeParseResult.Error(resolution.message)
            is DirectoryResolution.Success -> ServeParseResult.Success(
                ServeOptions(
                    store = ServeStore.RocksDb(resolution.normalizedPath),
                    host = host,
                    port = port,
                    bearerToken = bearerToken,
                    warnBearerTokenInArguments = cliInput.usesLiteralBearerToken,
                    allowInsecureRemoteBinding = merged.allowInsecureRemoteBinding ?: false,
                ),
            )
        }
        StoreType.FOUNDATION_DB -> {
            val dirParts = directory.split('/').filter { it.isNotBlank() }
            if (dirParts.isEmpty()) return ServeParseResult.Error("FoundationDB directory path is required.")
            val clusterFile = merged.clusterFile?.ifBlank { null } ?: defaultClusterFile()
            val options = ConnectCommand.FoundationOptions(
                directoryPath = dirParts,
                clusterFile = clusterFile,
            )
            ServeParseResult.Success(
                ServeOptions(
                    store = ServeStore.FoundationDb(options),
                    host = host,
                    port = port,
                    bearerToken = bearerToken,
                    warnBearerTokenInArguments = cliInput.usesLiteralBearerToken,
                    allowInsecureRemoteBinding = merged.allowInsecureRemoteBinding ?: false,
                ),
            )
        }
    }
}

private data class ServeCliInput(
    val input: ServeConfigInput,
    val configPath: String?,
    val usesLiteralBearerToken: Boolean,
)

private sealed class CliParseResult {
    data class Success(val input: ServeCliInput) : CliParseResult()
    data class Error(val reason: String) : CliParseResult()
}

private fun parseServeArguments(arguments: List<String>): CliParseResult {
    if (arguments.isEmpty()) return CliParseResult.Error("No arguments supplied.")

    var configPath: String? = null
    var storeType: StoreType? = null
    var directory: String? = null
    var clusterFile: String? = null
    var host: String? = null
    var port: Int? = null
    var bearerTokenSource: BearerTokenSource? = null
    var allowInsecureRemoteBinding: Boolean? = null

    var index = 0
    while (index < arguments.size) {
        val token = arguments[index]
        when {
            token == "--config" -> {
                if (configPath != null) return CliParseResult.Error("Config path provided multiple times.")
                if (index + 1 >= arguments.size) return CliParseResult.Error("`--config` requires a value.")
                configPath = arguments[++index]
            }
            token.startsWith("--config=") -> {
                if (configPath != null) return CliParseResult.Error("Config path provided multiple times.")
                configPath = token.substringAfter("=")
            }
            token == "rocksdb" || token == "rocks" -> {
                if (storeType != null) return CliParseResult.Error("Store type provided multiple times.")
                storeType = StoreType.ROCKS_DB
            }
            token == "foundationdb" || token == "fdb" -> {
                if (storeType != null) return CliParseResult.Error("Store type provided multiple times.")
                storeType = StoreType.FOUNDATION_DB
            }
            token.startsWith("--dir=") -> {
                if (directory != null) return CliParseResult.Error("Directory provided multiple times.")
                directory = token.substringAfter("=")
            }
            token == "--dir" -> {
                if (directory != null) return CliParseResult.Error("Directory provided multiple times.")
                if (index + 1 >= arguments.size) return CliParseResult.Error("`--dir` requires a value.")
                directory = arguments[++index]
            }
            token.startsWith("--cluster=") -> {
                if (clusterFile != null) return CliParseResult.Error("Cluster file provided multiple times.")
                clusterFile = token.substringAfter("=")
            }
            token == "--cluster" -> {
                if (clusterFile != null) return CliParseResult.Error("Cluster file provided multiple times.")
                if (index + 1 >= arguments.size) return CliParseResult.Error("`--cluster` requires a value.")
                clusterFile = arguments[++index]
            }
            token.startsWith("--host=") -> {
                if (host != null) return CliParseResult.Error("Host provided multiple times.")
                host = token.substringAfter("=")
            }
            token == "--host" -> {
                if (host != null) return CliParseResult.Error("Host provided multiple times.")
                if (index + 1 >= arguments.size) return CliParseResult.Error("`--host` requires a value.")
                host = arguments[++index]
            }
            token.startsWith("--port=") -> {
                if (port != null) return CliParseResult.Error("Port provided multiple times.")
                port = token.substringAfter("=").toIntOrNull()
                    ?: return CliParseResult.Error("Invalid port value.")
            }
            token == "--port" -> {
                if (port != null) return CliParseResult.Error("Port provided multiple times.")
                if (index + 1 >= arguments.size) return CliParseResult.Error("`--port` requires a value.")
                port = arguments[++index].toIntOrNull()
                    ?: return CliParseResult.Error("Invalid port value.")
            }
            token.startsWith("--bearer-token=") -> {
                if (bearerTokenSource != null) return CliParseResult.Error("Provide only one bearer token source.")
                bearerTokenSource = BearerTokenSource.Literal(token.substringAfter("="))
            }
            token == "--bearer-token" -> {
                if (bearerTokenSource != null) return CliParseResult.Error("Provide only one bearer token source.")
                if (index + 1 >= arguments.size) return CliParseResult.Error("`--bearer-token` requires a value.")
                bearerTokenSource = BearerTokenSource.Literal(arguments[++index])
            }
            token.startsWith("--bearer-token-env=") -> {
                if (bearerTokenSource != null) return CliParseResult.Error("Provide only one bearer token source.")
                bearerTokenSource = BearerTokenSource.Environment(token.substringAfter("="))
            }
            token == "--bearer-token-env" -> {
                if (bearerTokenSource != null) return CliParseResult.Error("Provide only one bearer token source.")
                if (index + 1 >= arguments.size) return CliParseResult.Error("`--bearer-token-env` requires a variable name.")
                bearerTokenSource = BearerTokenSource.Environment(arguments[++index])
            }
            token.startsWith("--bearer-token-file=") -> {
                if (bearerTokenSource != null) return CliParseResult.Error("Provide only one bearer token source.")
                bearerTokenSource = BearerTokenSource.File(token.substringAfter("="))
            }
            token == "--bearer-token-file" -> {
                if (bearerTokenSource != null) return CliParseResult.Error("Provide only one bearer token source.")
                if (index + 1 >= arguments.size) return CliParseResult.Error("`--bearer-token-file` requires a path.")
                bearerTokenSource = BearerTokenSource.File(arguments[++index])
            }
            token == "--bearer-token-stdin" -> {
                if (bearerTokenSource != null) return CliParseResult.Error("Provide only one bearer token source.")
                bearerTokenSource = BearerTokenSource.Stdin
            }
            token == "--allow-insecure-remote-binding" -> {
                if (allowInsecureRemoteBinding != null) {
                    return CliParseResult.Error("Insecure remote binding option provided multiple times.")
                }
                allowInsecureRemoteBinding = true
            }
            token.startsWith("-") -> return CliParseResult.Error("Unknown option $token")
            storeType == null -> storeType = parseStoreType(token)
                ?: return CliParseResult.Error("Unknown store type $token")
            directory == null -> directory = token
            else -> return CliParseResult.Error("Unexpected argument $token")
        }
        index += 1
    }

    return CliParseResult.Success(
        ServeCliInput(
            input = ServeConfigInput(
                storeType = storeType,
                directory = directory,
                clusterFile = clusterFile,
                host = host,
                port = port,
                bearerTokenSource = bearerTokenSource,
                allowInsecureRemoteBinding = allowInsecureRemoteBinding,
            ),
            configPath = configPath,
            usesLiteralBearerToken = bearerTokenSource is BearerTokenSource.Literal,
        ),
    )
}

internal sealed class ConfigParseResult {
    data class Success(val input: ServeConfigInput) : ConfigParseResult()
    data class Error(val reason: String) : ConfigParseResult()
}

internal fun parseServeConfig(contents: String): ConfigParseResult {
    var storeType: StoreType? = null
    var directory: String? = null
    var clusterFile: String? = null
    var host: String? = null
    var port: Int? = null
    var bearerTokenSource: BearerTokenSource? = null
    var allowInsecureRemoteBinding: Boolean? = null
    val seenKeys = mutableSetOf<String>()

    contents.lineSequence().forEachIndexed { index, line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) return@forEachIndexed

        val separatorIndex = trimmed.indexOfFirst { it == '=' || it == ':' }
        if (separatorIndex <= 0) {
            return ConfigParseResult.Error("Invalid config line ${index + 1}: $line")
        }
        val key = trimmed.substring(0, separatorIndex).trim().lowercase()
        val rawValue = trimmed.substring(separatorIndex + 1).trim()
        val value = rawValue.trim('"', '\'')

        val canonicalKey = when (key) {
            "store", "type" -> "store"
            "dir", "directory", "path" -> "directory"
            "cluster", "clusterfile" -> "cluster"
            "bearertoken", "bearer-token", "bearertokenenv", "bearer-token-env", "bearertokenfile", "bearer-token-file", "bearertokenstdin", "bearer-token-stdin" -> "bearer-token-source"
            "allowinsecureremotebinding", "allow-insecure-remote-binding" -> "allow-insecure-remote-binding"
            else -> key
        }
        if (!seenKeys.add(canonicalKey)) {
            return ConfigParseResult.Error("Duplicate config key $key on line ${index + 1}")
        }

        when (key) {
            "store", "type" -> storeType = parseStoreType(value)
                ?: return ConfigParseResult.Error("Unknown store type on line ${index + 1}")
            "dir", "directory", "path" -> directory = value
            "cluster", "clusterfile" -> clusterFile = value
            "host" -> host = value
            "port" -> port = value.toIntOrNull() ?: return ConfigParseResult.Error("Invalid port on line ${index + 1}")
            "bearertoken", "bearer-token" -> bearerTokenSource = BearerTokenSource.Literal(value)
            "bearertokenenv", "bearer-token-env" -> bearerTokenSource = BearerTokenSource.Environment(value)
            "bearertokenfile", "bearer-token-file" -> bearerTokenSource = BearerTokenSource.File(value)
            "bearertokenstdin", "bearer-token-stdin" -> {
                if (value.isNotEmpty()) return ConfigParseResult.Error("Bearer token stdin source must be blank on line ${index + 1}")
                bearerTokenSource = BearerTokenSource.Stdin
            }
            "allowinsecureremotebinding", "allow-insecure-remote-binding" -> {
                allowInsecureRemoteBinding = value.toBooleanStrictOrNull()
                    ?: return ConfigParseResult.Error("Invalid boolean on line ${index + 1}")
            }
            else -> return ConfigParseResult.Error("Unknown config key $key on line ${index + 1}")
        }
    }

    return ConfigParseResult.Success(
        ServeConfigInput(
            storeType = storeType,
            directory = directory,
            clusterFile = clusterFile,
            host = host,
            port = port,
            bearerTokenSource = bearerTokenSource,
            allowInsecureRemoteBinding = allowInsecureRemoteBinding,
        )
    )
}

private fun parseStoreType(value: String): StoreType? = when (value.lowercase()) {
    "rocksdb", "rocks" -> StoreType.ROCKS_DB
    "foundationdb", "fdb" -> StoreType.FOUNDATION_DB
    else -> null
}

private fun defaultClusterFile(): String = "store/foundationdb/fdb.cluster"
