package io.maryk.cli

import com.varabyte.kotter.foundation.input.InputCompleter
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys
import kotlin.math.max
import kotlin.math.min

class OutputViewerInteraction(
    private var lines: List<String>,
    terminalHeight: Int,
    private var saveContext: SaveContext? = null,
    private val deleteContext: DeleteContext? = null,
    private val loadContext: LoadContext? = null,
    private val returnInteraction: CliInteraction? = null,
    private val headerLines: List<String> = emptyList(),
    private val showChrome: Boolean = true,
) : CliInteraction {
    override val promptLabel: String = "view> "
    override val introLines: List<String> = if (showChrome) {
        val introSaveContext = saveContext
        val commandLine = buildList {
            if (introSaveContext != null) {
                val formatOptions = if (introSaveContext.kotlinGenerator != null) {
                    "--yaml|--json|--proto|--kotlin"
                } else {
                    "--yaml|--json|--proto"
                }
                val noDepsSuffix = if (introSaveContext.supportsNoDeps) " [--no-deps]" else ""
                add("save <dir> [$formatOptions] [--package <name>] [--meta]$noDepsSuffix")
            }
            if (loadContext != null) {
                add("load <file> [--yaml|--json|--proto] [--if-version <n>] [--meta]")
                add("set <ref> <value> [--if-version <n>]")
                add("unset <ref> [--if-version <n>]")
                add("append <ref> <value> [--if-version <n>]")
                add("remove <ref> <value> [--if-version <n>]")
            }
            if (deleteContext != null) {
                add("delete [--hard]")
            }
            if (returnInteraction != null) {
                add("close")
            }
            addAll(EXIT_COMMANDS)
        }.joinToString(separator = " | ")
        listOf(
            buildString {
                append("Viewing output. Use Up/Down to scroll, PgUp/PgDn for pages, Home/End for ends, ")
                if (returnInteraction != null) {
                    append("close to return, ")
                }
                append("q/quit/exit to close.")
            },
            "Commands: $commandLine",
        )
    } else {
        emptyList()
    }

    private val viewHeight: Int = if (showChrome) {
        max(1, terminalHeight - FOOTER_AND_PROMPT_LINES - headerLines.size)
    } else {
        max(1, lines.size + headerLines.size)
    }
    private var offset: Int = 0
    private var statusMessage: String? = null
    private var pendingDelete: Boolean = false
    private var pendingHardDelete: Boolean = false
    private val referencePaths by lazy { loadContext?.let { collectReferencePaths(it.dataModel) }.orEmpty() }
    private val completer: InputCompleter = object : InputCompleter {
        override fun complete(input: String): String? {
            val trimmed = input.trimStart()
            if (trimmed.isEmpty()) {
                return null
            }

            val endsWithSpace = input.lastOrNull()?.isWhitespace() == true
            val tokens = trimmed.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
            val currentToken = if (endsWithSpace) "" else tokens.lastOrNull().orEmpty()

            if (pendingDelete) {
                return completeToken(currentToken, YES_NO_OPTIONS)
            }

            val command = tokens.first().lowercase()
            if (command == "save") {
                val resolvedSaveContext = saveContext ?: return null
                if (tokens.size == 1 && !endsWithSpace) {
                    return completeToken(currentToken, listOf("save"))
                }
                if (currentToken.startsWith("--")) {
                    val options = buildList {
                        add("--yaml")
                        add("--json")
                        add("--proto")
                        if (resolvedSaveContext.kotlinGenerator != null) add("--kotlin")
                        add("--package")
                        add("--meta")
                        if (resolvedSaveContext.supportsNoDeps) add("--no-deps")
                    }
                    return completeToken(currentToken, options)
                }
                if (endsWithSpace) {
                    return if (resolvedSaveContext.kotlinGenerator != null) "--kotlin" else "--yaml"
                }
                return null
            }

            if (command == "load") {
                if (loadContext == null) return null
                if (tokens.size == 1 && !endsWithSpace) {
                    return completeToken(currentToken, listOf("load"))
                }
                if (currentToken.startsWith("--")) {
                    return completeToken(currentToken, listOf("--yaml", "--json", "--proto", "--if-version", "--meta"))
                }
                if (endsWithSpace && tokens.size > 1 && tokens.drop(1).any { !it.startsWith("--") }) {
                    return "--yaml"
                }
                return null
            }

            if (command == "set" || command == "unset" || command == "append" || command == "remove") {
                if (loadContext == null) return null
                if (tokens.size == 1 && !endsWithSpace) {
                    return completeToken(currentToken, listOf(command))
                }
                if (tokens.size == 1 && endsWithSpace) {
                    return completeToken("", referencePaths)
                }
                if (tokens.size == 2 && !endsWithSpace && !currentToken.startsWith("--")) {
                    return completeToken(currentToken, referencePaths)
                }
                if (currentToken.startsWith("--")) {
                    return completeToken(currentToken, listOf("--if-version"))
                }
                return null
            }

            if (command == "delete") {
                if (deleteContext == null) return null
                if (currentToken.startsWith("--")) {
                    return completeToken(currentToken, listOf("--hard"))
                }
                if (endsWithSpace) {
                    return "--hard"
                }
                return null
            }

            val commands = buildList {
                if (saveContext != null) add("save")
                if (loadContext != null) add("load")
                if (loadContext != null) {
                    add("set")
                    add("unset")
                    add("append")
                    add("remove")
                }
                if (deleteContext != null) add("delete")
                if (returnInteraction != null) add("close")
                addAll(EXIT_COMMANDS)
            }
            return completeToken(currentToken, commands)
        }
    }

    override fun inputCompleter(): InputCompleter = completer

    override fun promptLines(): List<String> {
        if (lines.isEmpty()) {
            return if (showChrome) {
                headerLines + listOf("<no output>", footerLine(0, 0, 0))
            } else {
                headerLines
            }
        }

        val end = min(lines.size, offset + viewHeight)
        val visible = lines.subList(offset, end)
        return if (showChrome) {
            val footer = footerLine(offset + 1, end, lines.size)
            headerLines + visible + footer
        } else {
            headerLines + visible
        }
    }

    override fun onInput(input: String): InteractionResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            statusMessage = null
            return InteractionResult.Stay()
        }

        if (pendingDelete) {
            return handleDeleteConfirmation(trimmed)
        }

        when {
            trimmed.equals("close", ignoreCase = true) -> {
                val target = returnInteraction
                return if (target != null) {
                    InteractionResult.Continue(target, showIntro = false)
                } else {
                    statusMessage = "Unknown command: close"
                    InteractionResult.Stay(lines = statusLines())
                }
            }

            trimmed.equals("q", ignoreCase = true)
                || trimmed.equals("quit", ignoreCase = true)
                || trimmed.equals("exit", ignoreCase = true) -> {
                return InteractionResult.Complete(lines = emptyList())
            }

            trimmed.equals("save", ignoreCase = true)
                || trimmed.startsWith("save ", ignoreCase = true) -> {
                val resolvedSaveContext = saveContext ?: run {
                    statusMessage = "Unknown command: save"
                    return InteractionResult.Stay(lines = statusLines())
                }
                val args = CommandLineParser.parse(trimmed)
                val tokens = when (args) {
                    is CommandLineParser.ParseResult.Success -> args.tokens
                    is CommandLineParser.ParseResult.Error -> {
                        statusMessage = "Save failed: ${args.message}"
                        return InteractionResult.Stay(lines = statusLines())
                    }
                }

                val options = tokens.drop(1)
                val parseResult = parseSaveOptions(options, resolvedSaveContext)
                if (parseResult is SaveOptionsResult.Error) {
                    statusMessage = parseResult.message
                    return InteractionResult.Stay(lines = statusLines())
                }

                val saveOptions = (parseResult as SaveOptionsResult.Success).options

                try {
                    statusMessage = resolvedSaveContext.save(
                        directory = saveOptions.directory,
                        format = saveOptions.format,
                        includeMeta = saveOptions.includeMeta,
                        packageName = saveOptions.packageName,
                        noDeps = saveOptions.noDeps,
                    )
                } catch (e: Throwable) {
                    statusMessage = "Save failed: ${e.message ?: e::class.simpleName}"
                }
                return InteractionResult.Stay(lines = statusLines())
            }

            trimmed.equals("load", ignoreCase = true)
                || trimmed.startsWith("load ", ignoreCase = true) -> {
                val resolvedLoadContext = loadContext ?: run {
                    statusMessage = "Unknown command: load"
                    return InteractionResult.Stay(lines = statusLines())
                }
                val args = CommandLineParser.parse(trimmed)
                val tokens = when (args) {
                    is CommandLineParser.ParseResult.Success -> args.tokens
                    is CommandLineParser.ParseResult.Error -> {
                        statusMessage = "Load failed: ${args.message}"
                        return InteractionResult.Stay(lines = statusLines())
                    }
                }

                val options = tokens.drop(1)
                val parseResult = parseLoadOptions(options)
                if (parseResult is LoadOptionsResult.Error) {
                    statusMessage = parseResult.message
                    return InteractionResult.Stay(lines = statusLines())
                }

                val loadOptions = (parseResult as LoadOptionsResult.Success).options

                val loadResult = try {
                    resolvedLoadContext.loadResult(
                        path = loadOptions.path,
                        format = loadOptions.format,
                        ifVersion = loadOptions.ifVersion,
                        useMeta = loadOptions.useMeta,
                    )
                } catch (e: Throwable) {
                    ApplyResult("Load failed: ${e.message ?: e::class.simpleName}", success = false)
                }
                val refreshError = if (loadResult.success) refreshView() else null
                statusMessage = mergeStatusMessage(loadResult.message, refreshError)
                return InteractionResult.Stay(lines = statusLines())
            }

            trimmed.equals("set", ignoreCase = true)
                || trimmed.startsWith("set ", ignoreCase = true) -> {
                return handleInlineEdit("set", trimmed)
            }

            trimmed.equals("unset", ignoreCase = true)
                || trimmed.startsWith("unset ", ignoreCase = true) -> {
                return handleInlineEdit("unset", trimmed)
            }

            trimmed.equals("append", ignoreCase = true)
                || trimmed.startsWith("append ", ignoreCase = true) -> {
                return handleInlineEdit("append", trimmed)
            }

            trimmed.equals("remove", ignoreCase = true)
                || trimmed.startsWith("remove ", ignoreCase = true) -> {
                return handleInlineEdit("remove", trimmed)
            }

            trimmed.equals("delete", ignoreCase = true)
                || trimmed.startsWith("delete ", ignoreCase = true) -> {
                val resolvedDeleteContext = deleteContext ?: run {
                    statusMessage = "Unknown command: delete"
                    return InteractionResult.Stay(lines = statusLines())
                }
                val tokens = trimmed.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
                val parseResult = parseDeleteOptions(tokens.drop(1))
                if (parseResult is DeleteOptionsResult.Error) {
                    statusMessage = parseResult.message
                    return InteractionResult.Stay(lines = statusLines())
                }
                val options = (parseResult as DeleteOptionsResult.Success).options
                pendingDelete = true
                pendingHardDelete = options.hardDelete
                statusMessage = buildString {
                    append("Delete ${resolvedDeleteContext.label}")
                    if (pendingHardDelete) append(" (hard)")
                    append("? Type yes or no.")
                }
                return InteractionResult.Stay(lines = statusLines())
            }

            else -> {
                statusMessage = "Unknown command: $trimmed"
                return InteractionResult.Stay(lines = statusLines())
            }
        }
    }

    private fun handleInlineEdit(command: String, input: String): InteractionResult {
        val resolvedLoadContext = loadContext ?: run {
            statusMessage = "Unknown command: $command"
            return InteractionResult.Stay(lines = statusLines())
        }

        val args = CommandLineParser.parse(input)
        val tokens = when (args) {
            is CommandLineParser.ParseResult.Success -> args.tokens
            is CommandLineParser.ParseResult.Error -> {
                statusMessage = "${command.replaceFirstChar { it.uppercase() }} failed: ${args.message}"
                return InteractionResult.Stay(lines = statusLines())
            }
        }

        val parseResult = parseInlineOptions(tokens.drop(1), requiresValue = command != "unset")
        if (parseResult is InlineOptionsResult.Error) {
            statusMessage = parseResult.message
            return InteractionResult.Stay(lines = statusLines())
        }
        val options = (parseResult as InlineOptionsResult.Success).options

        val result = try {
            when (command) {
                "set" -> applySet(resolvedLoadContext, options)
                "unset" -> applyUnset(resolvedLoadContext, options)
                "append" -> applyAppend(resolvedLoadContext, options)
                "remove" -> applyRemove(resolvedLoadContext, options)
                else -> ApplyResult("Unknown command: $command", success = false)
            }
        } catch (e: Throwable) {
            ApplyResult(
                "${command.replaceFirstChar { it.uppercase() }} failed: ${e.message ?: e::class.simpleName}",
                success = false,
            )
        }

        val refreshError = if (result.success) refreshView() else null
        statusMessage = mergeStatusMessage(result.message, refreshError)
        return InteractionResult.Stay(lines = statusLines())
    }

    private fun refreshView(): String? {
        val resolvedLoadContext = loadContext
            ?: return "View refresh not available for this output."
        return when (val result = resolvedLoadContext.refreshView()) {
            is RefreshResult.Success -> {
                lines = result.lines
                saveContext = result.saveContext
                offset = min(offset, max(0, lines.size - viewHeight))
                null
            }
            is RefreshResult.Error -> result.message
        }
    }

    private fun mergeStatusMessage(base: String, refreshError: String?): String =
        if (refreshError == null) base else "$base (view refresh failed: $refreshError)"

    private fun handleDeleteConfirmation(input: String): InteractionResult {
        val resolvedDeleteContext = deleteContext
            ?: return InteractionResult.Stay(lines = listOf("Delete not available for this output."))
        return when (input.lowercase()) {
            "yes", "y" -> {
                pendingDelete = false
                val lines = try {
                    resolvedDeleteContext.onDelete(pendingHardDelete)
                } catch (e: Throwable) {
                    listOf("Delete failed: ${e.message ?: e::class.simpleName}")
                }
                pendingHardDelete = false
                InteractionResult.Stay(lines = lines)
            }
            "no", "n", "cancel" -> {
                pendingDelete = false
                pendingHardDelete = false
                InteractionResult.Stay(lines = listOf("Delete cancelled."))
            }
            else -> InteractionResult.Stay(lines = listOf("Type yes or no."))
        }
    }

    override fun onKeyPressed(key: Key): InteractionKeyResult? {
        if (!showChrome) return null
        val previous = offset
        val maxOffset = max(0, lines.size - viewHeight)
        when (key) {
            Keys.UP -> offset = max(0, offset - 1)
            Keys.DOWN -> offset = min(maxOffset, offset + 1)
            Keys.PAGE_UP -> offset = max(0, offset - viewHeight)
            Keys.PAGE_DOWN -> offset = min(maxOffset, offset + viewHeight)
            Keys.HOME -> offset = 0
            Keys.END -> offset = maxOffset
            else -> return null
        }
        return if (offset != previous) InteractionKeyResult.Rerender else null
    }

    private fun statusLines(): List<String> = statusMessage?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()

    private fun footerLine(start: Int, end: Int, total: Int): String =
        buildString {
            append("Lines $start-$end of $total  (Up/Down PgUp/PgDn Home/End")
            if (returnInteraction != null) {
                append(" close")
            }
            append(" q/quit/exit)")
        }

    private companion object {
        private const val FOOTER_AND_PROMPT_LINES = 3
        private val EXIT_COMMANDS = listOf("q", "quit", "exit")
        private val YES_NO_OPTIONS = listOf("yes", "no")
        private val WHITESPACE_REGEX = Regex("\\s+")

        private fun completeToken(current: String, candidates: List<String>): String? {
            val match = candidates.firstOrNull { it.startsWith(current, ignoreCase = true) } ?: return null
            return match.drop(current.length)
        }
    }
}
