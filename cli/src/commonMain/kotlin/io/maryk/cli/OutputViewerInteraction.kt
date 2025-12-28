package io.maryk.cli

import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys
import com.varabyte.kotter.foundation.input.InputCompleter
import kotlin.math.max
import kotlin.math.min

class OutputViewerInteraction(
    private val lines: List<String>,
    terminalHeight: Int,
    private val saveContext: SaveContext? = null,
    private val deleteContext: DeleteContext? = null,
    private val headerLines: List<String> = emptyList(),
    private val showChrome: Boolean = true,
) : CliInteraction {
    override val promptLabel: String = "view> "
    override val introLines: List<String> = if (showChrome) {
        val commandLine = buildList {
            if (saveContext != null) {
                add("save <dir> [--yaml|--json|--proto] [--meta]")
            }
            if (deleteContext != null) {
                add("delete [--hard]")
            }
            addAll(EXIT_COMMANDS)
        }.joinToString(separator = " | ")
        listOf(
            "Viewing output. Use Up/Down to scroll, PgUp/PgDn for pages, Home/End for ends, q/quit/exit to close.",
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
                if (saveContext == null) return null
                if (tokens.size == 1 && !endsWithSpace) {
                    return completeToken(currentToken, listOf("save"))
                }
                if (currentToken.startsWith("--")) {
                    return completeToken(currentToken, listOf("--yaml", "--json", "--proto", "--meta"))
                }
                if (endsWithSpace) {
                    return "--yaml"
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
                if (deleteContext != null) add("delete")
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
                val parseResult = parseSaveOptions(options)
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
                    )
                } catch (e: Throwable) {
                    statusMessage = "Save failed: ${e.message ?: e::class.simpleName}"
                }
                return InteractionResult.Stay(lines = statusLines())
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

    private data class SaveOptions(
        val directory: String,
        val format: SaveFormat,
        val includeMeta: Boolean,
    )

    private sealed class SaveOptionsResult {
        data class Success(val options: SaveOptions) : SaveOptionsResult()
        data class Error(val message: String) : SaveOptionsResult()
    }

    private data class DeleteOptions(
        val hardDelete: Boolean,
    )

    private sealed class DeleteOptionsResult {
        data class Success(val options: DeleteOptions) : DeleteOptionsResult()
        data class Error(val message: String) : DeleteOptionsResult()
    }

    private fun parseSaveOptions(tokens: List<String>): SaveOptionsResult {
        var directory: String? = null
        var includeMeta = false
        var format: SaveFormat? = null

        tokens.forEach { token ->
            when (token.lowercase()) {
                "--meta" -> includeMeta = true
                "--yaml" -> {
                    val next = selectFormat(format, SaveFormat.YAML) ?: return SaveOptionsResult.Error(
                        "Choose only one format: --yaml, --json, or --proto"
                    )
                    format = next
                }
                "--json" -> {
                    val next = selectFormat(format, SaveFormat.JSON) ?: return SaveOptionsResult.Error(
                        "Choose only one format: --yaml, --json, or --proto"
                    )
                    format = next
                }
                "--proto" -> {
                    val next = selectFormat(format, SaveFormat.PROTO) ?: return SaveOptionsResult.Error(
                        "Choose only one format: --yaml, --json, or --proto"
                    )
                    format = next
                }
                else -> if (token.startsWith("--")) {
                    return SaveOptionsResult.Error("Unknown option: $token")
                } else if (directory == null) {
                    directory = token
                } else {
                    return SaveOptionsResult.Error("Unexpected argument: $token")
                }
            }
        }

        val resolvedDir = directory ?: "./"

        return SaveOptionsResult.Success(
            SaveOptions(
                directory = resolvedDir,
                format = format ?: SaveFormat.YAML,
                includeMeta = includeMeta,
            )
        )
    }

    private fun selectFormat(current: SaveFormat?, next: SaveFormat): SaveFormat? {
        return if (current != null && current != next) null else next
    }

    private fun parseDeleteOptions(tokens: List<String>): DeleteOptionsResult {
        var hardDelete = false
        tokens.forEach { token ->
            when (token.lowercase()) {
                "--hard" -> hardDelete = true
                else -> if (token.startsWith("--")) {
                    return DeleteOptionsResult.Error("Unknown option: $token")
                } else {
                    return DeleteOptionsResult.Error("Delete does not accept additional arguments.")
                }
            }
        }
        return DeleteOptionsResult.Success(DeleteOptions(hardDelete = hardDelete))
    }

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
        "Lines $start-$end of $total  (Up/Down PgUp/PgDn Home/End q/quit/exit)"

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
