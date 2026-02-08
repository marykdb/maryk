package io.maryk.cli.commands

/**
 * Lists the available commands and their descriptions.
 */
class HelpCommand : Command {
    override val name: String = "help"
    override val description: String = "Show available commands."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        val commands = context.registry.list()
            .sortedBy { it.name }

        if (arguments.isNotEmpty()) {
            val target = arguments.first().lowercase()
            val known = commands.firstOrNull { it.name == target }
            val help = helpFor(target)
            if (known == null || help == null) {
                val longestName = commands.maxOfOrNull { it.name.length } ?: 4
                val lines = buildList {
                    add("Unknown command `$target`.")
                    add("Available commands:")
                    commands.forEach { command ->
                        val paddedName = command.name.padEnd(longestName)
                        add("  $paddedName  ${command.description}")
                    }
                    add("Use `help <command>` for details.")
                }
                return CommandResult(lines = lines, isError = true)
            }
            return CommandResult(lines = help)
        }

        val longestName = commands.maxOfOrNull { it.name.length } ?: 4
        val lines = buildList {
            add("Available commands:")
            commands.forEach { command ->
                val paddedName = command.name.padEnd(longestName)
                add("  $paddedName  ${command.description}")
            }
            add("Use `help <command>` for details.")
        }

        return CommandResult(lines = lines)
    }

    private fun helpFor(command: String): List<String>? {
        return when (command) {
            "help" -> listOf(
                "help [command]",
                "Show available commands, or detailed help for a specific command.",
                "Example: help scan",
            )
            "connect" -> listOf(
                "connect <store> [options]",
                "Connect to a store (rocksdb or foundationdb).",
                "Usage:",
                "  connect rocksdb --dir <directory>",
                "  connect foundationdb --dir <directory> [--cluster <cluster file>] [--tenant <tenant>]",
                "Notes:",
                "  - Run `connect` without arguments to use the interactive setup.",
            )
            "serve" -> listOf(
                "serve <store> [options]",
                "Serve a local store over HTTP.",
                "Usage:",
                "  serve rocksdb --dir <directory> [--host <host>] [--port <port>]",
                "  serve foundationdb --dir <directory> [--cluster <cluster file>] [--tenant <tenant>] [--host <host>] [--port <port>]",
                "  serve --config <file>",
                "Notes:",
                "  - Config files support key/value (`key=value`) and YAML-style (`key: value`) entries.",
                "  - Warning: no auth or TLS; bind to localhost or use SSH tunneling.",
            )
            "disconnect" -> listOf(
                "disconnect",
                "Close the current store connection.",
            )
            "list" -> listOf(
                "list",
                "List available data models in the connected store.",
            )
            "model" -> listOf(
                "model [--with-deps] [--key-index-format] [<name|id>]",
                "model --all [--with-deps] [--key-index-format]",
                "Render model definitions in YAML.",
                "Options:",
                "  --with-deps          Include dependent definitions.",
                "  --key-index-format   Include key/index format details.",
                "  --all                Render all models in the store.",
                "Notes:",
                "  - `--all` cannot be combined with a model name or id.",
                "  - Use `save` in the viewer to export YAML/JSON/PROTO, or Kotlin output with --kotlin --package.",
            )
            "add" -> listOf(
                "add <model> <file> [--yaml|--json|--proto] [--meta] [--key <base64>]",
                "Add a new record from a file.",
                "Options:",
                "  --yaml|--json|--proto  Input format (default: --yaml).",
                "  --meta                 Load metadata file saved via `save --meta`.",
                "  --key <base64>          Provide an explicit key for the new record.",
                "Notes:",
                "  - YAML/JSON files can contain a list of objects to add multiple records.",
                "  - Use `-` as the file path to read from stdin.",
                "  - If --key is omitted, the key is derived from values (UUID keys generate a new random key).",
                "  - When --meta is used, --key must match the metadata key.",
            )
            "get" -> listOf(
                "get <model> <key> [--include-deleted] [subcommand ...]",
                "Fetch a record by key and show it in YAML, or run a subcommand inline.",
                "Subcommands:",
                "  save <dir> [--yaml|--json|--proto] [--meta]",
                "  load <file> [--yaml|--json|--proto] [--if-version <n>] [--meta]",
                "  set <ref> <value> [--if-version <n>]",
                "  unset <ref> [--if-version <n>]",
                "  append <ref> <value> [--if-version <n>]",
                "  remove <ref> <value> [--if-version <n>]",
                "  delete [--hard]",
                "  undelete [--if-version <n>]",
                "Notes:",
                "  - Values are parsed as Maryk YAML scalars for the referenced type.",
                "  - `append`/`remove` only support list or set references.",
                "  - `--if-version` applies optimistic concurrency checks.",
                "  - Use `--include-deleted` to access soft-deleted records.",
            )
            "changes" -> listOf(
                "changes <model> <key> [--from-version <n>] [--to-version <n>] [--limit <n>] [--include-deleted]",
                "Show versioned changes for a record.",
                "Options:",
                "  --from-version <n>  Start version (inclusive, default 0).",
                "  --to-version <n>    End version (inclusive).",
                "  --limit <n>         Max versions to return (default 1000).",
                "  --include-deleted   Include soft-deleted records.",
            )
            "undelete" -> listOf(
                "undelete <model> <key> [--if-version <n>]",
                "Restore a soft-deleted record.",
            )
            "scan" -> listOf(
                "scan <model> [options]",
                "Scan records and browse results.",
                "Options:",
                "  --start-key <base64>",
                "  --limit <n>",
                "  --include-start | --exclude-start",
                "  --to-version <n>",
                "  --include-deleted",
                "  --where <expr>",
                "  --order <ref,...>",
                "  --select <ref,...>",
                "  --show <ref,...>",
                "  --max-chars <n>",
                "Viewer commands:",
                "  get | show <refs> | filter <expr> | save | load | delete | close",
                "Notes:",
                "  - Filter expressions use Maryk YAML tags (e.g. \"!Equals { field: value }\").",
                "  - For performance, use --order on indexed properties.",
            )
            else -> null
        }
    }
}
