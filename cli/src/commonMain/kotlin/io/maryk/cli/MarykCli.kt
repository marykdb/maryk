package io.maryk.cli

import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.foundation.input.input
import com.varabyte.kotter.foundation.input.getInput
import com.varabyte.kotter.foundation.input.onInputEntered
import com.varabyte.kotter.foundation.input.onKeyPressed
import com.varabyte.kotter.foundation.input.runUntilInputEntered
import com.varabyte.kotter.foundation.input.setInput
import com.varabyte.kotter.foundation.input.Keys
import com.varabyte.kotter.foundation.text.text
import com.varabyte.kotter.foundation.text.textLine
import io.maryk.cli.commands.CommandRegistry
import io.maryk.cli.commands.ConnectCommand
import io.maryk.cli.commands.DisconnectCommand
import io.maryk.cli.commands.GetCommand
import io.maryk.cli.commands.HelpCommand
import io.maryk.cli.commands.ListCommand
import io.maryk.cli.commands.ModelCommand
import io.maryk.cli.commands.ScanCommand
import io.maryk.cli.commands.AddCommand
import io.maryk.cli.commands.ChangesCommand
import io.maryk.cli.commands.UndeleteCommand
import io.maryk.cli.commands.registerAll

fun main(args: Array<String>) {
    runCatching {
        val state = CliState()
        val environment = BasicCliEnvironment
        val registry = defaultRegistry(state, environment)
        when (val oneShot = parseOneShotArgs(args)) {
            is OneShotParseResult.Error -> {
                printOneShotUsage()
                println("Error: ${oneShot.message}")
                exitWithCode(1)
            }
            is OneShotParseResult.Success -> {
                val exitCode = runOneShot(registry, oneShot.options)
                exitWithCode(exitCode)
            }
            null -> {
                if (!isInteractiveTerminal()) {
                    printNonInteractiveHelp(registry)
                    return
                }
                MarykCli(registry).run()
            }
        }
    }.onFailure { t ->
        println("CLI fatal error: ${t::class.simpleName}: ${t.message ?: "no message"}")
        t.printStackTrace()
    }
}

class MarykCli(
    private val registry: CommandRegistry,
) {
    fun run() = session {
        val prompt = "> "
        val commandCompleter = CliInputCompleter(registry)
        val state = registry.state
        val commandHistory = mutableListOf<String>()

        fun renderOutput(
            lines: List<String>,
            leadingBlank: Boolean = false,
            trailingBlank: Boolean = false,
        ) {
            // Normalize output to avoid leading/trailing blank lines while supporting a spacer.
            if (lines.isEmpty() && !leadingBlank && !trailingBlank) return
            val normalized = lines
                .dropWhile { it.isEmpty() }
                .dropLastWhile { it.isEmpty() }
            val rendered = buildList {
                if (leadingBlank && (normalized.isNotEmpty() || trailingBlank)) {
                    add("")
                }
                addAll(normalized)
                if (trailingBlank && (normalized.isNotEmpty() || leadingBlank)) {
                    add("")
                }
            }
            if (rendered.isEmpty()) return
            section {
                rendered.forEach { line -> textLine(line) }
            }.run()
        }

        fun promptForCommand(): String {
            var entered = ""
            var historyIndex: Int? = null
            var draftInput: String? = null
            val inputId = "command-input"
            section {
                text(prompt)
                input(completer = commandCompleter, id = inputId)
            }.runUntilInputEntered {
                onInputEntered {
                    entered = input
                }
                onKeyPressed {
                    when (key) {
                        Keys.TAB -> {
                            val current = getInput(inputId).orEmpty()
                            val completion = commandCompleter.complete(current)
                            if (!completion.isNullOrEmpty()) {
                                setInput(current + completion, id = inputId)
                            }
                        }
                        Keys.UP -> {
                            if (commandHistory.isEmpty()) return@onKeyPressed
                            val current = getInput(inputId).orEmpty()
                            if (historyIndex == null) {
                                draftInput = current
                                historyIndex = commandHistory.lastIndex
                            } else if (historyIndex!! > 0) {
                                historyIndex = historyIndex!! - 1
                            }
                            val nextIndex = historyIndex ?: return@onKeyPressed
                            setInput(commandHistory[nextIndex], id = inputId)
                        }
                        Keys.DOWN -> {
                            val index = historyIndex ?: return@onKeyPressed
                            val nextIndex = index + 1
                            if (nextIndex >= commandHistory.size) {
                                historyIndex = null
                                setInput(draftInput.orEmpty(), id = inputId)
                            } else {
                                historyIndex = nextIndex
                                setInput(commandHistory[nextIndex], id = inputId)
                            }
                        }
                        else -> Unit
                    }
                }
            }
            return entered
        }

        data class InteractionOutcome(
            val lines: List<String>,
            val allowViewer: Boolean,
            val leadingBlank: Boolean,
            val trailingBlank: Boolean,
            val saveContext: SaveContext?,
            val deleteContext: DeleteContext?,
            val loadContext: LoadContext?,
        )

        fun runInteraction(): InteractionOutcome {
            val initial = state.currentInteraction
                ?: return InteractionOutcome(
                    lines = emptyList(),
                    allowViewer = true,
                    leadingBlank = true,
                    trailingBlank = true,
                    saveContext = null,
                    deleteContext = null,
                    loadContext = null,
                )

            var currentInteraction = initial
            var messageLines: List<String> = emptyList()
            var completedLines: List<String> = emptyList()
            var completedSaveContext: SaveContext? = null
            var completedDeleteContext: DeleteContext? = null
            var completedLoadContext: LoadContext? = null
            val transcriptLines = mutableListOf<String>()
            var finalRenderLines: List<String>? = null
            var finalizeInSection = false
            var finalRendered = false
            var finalizeSignal: (() -> Unit)? = null
            var allowViewer = currentInteraction !is OutputViewerInteraction && currentInteraction.allowViewerOnComplete
            var renderActive = true

            val interactionSection = section {
                if (!renderActive) return@section
                val finalLines = finalRenderLines
                if (finalLines != null) {
                    finalLines.forEach { line -> textLine(line) }
                    return@section
                }
                val lines = buildList {
                    if (!state.interactionIntroShown()) {
                        addAll(currentInteraction.introLines)
                    }
                    addAll(currentInteraction.promptLines())
                    if (messageLines.isNotEmpty()) {
                        addAll(messageLines)
                    }
                }
                lines.forEach { line -> textLine(line) }
                text(currentInteraction.promptLabel)
                input(completer = currentInteraction.inputCompleter())
            }

            interactionSection.onFinishing {
                if (finalRenderLines == null) {
                    renderActive = false
                    messageLines = emptyList()
                    interactionSection.requestRerender()
                }
            }

            interactionSection.onRendered {
                if (finalizeInSection && !finalRendered) {
                    finalRendered = true
                    finalizeSignal?.invoke()
                    removeListener = true
                }
            }

            interactionSection.run {
                finalizeSignal = { signal() }
                onInputEntered {
                    val trimmed = input.trim()
                    val result = currentInteraction.onInput(input)
                    val resultLines = when (result) {
                        is InteractionResult.Continue -> result.lines
                        is InteractionResult.Stay -> result.lines
                        is InteractionResult.Complete -> result.lines
                    }
                    if (currentInteraction is OutputViewerInteraction &&
                        trimmed.isNotEmpty() &&
                        trimmed.lowercase() !in EXIT_TOKENS &&
                        resultLines.isNotEmpty()
                    ) {
                        transcriptLines.add("${currentInteraction.promptLabel}$trimmed")
                        transcriptLines.addAll(resultLines)
                    }
                    when (result) {
                        is InteractionResult.Stay -> {
                            state.markInteractionIntroShown()
                            messageLines = result.lines
                            clearInput()
                            rerender()
                        }
                        is InteractionResult.Continue -> {
                            state.markInteractionIntroShown()
                            state.replaceInteraction(result.next, result.showIntro)
                            currentInteraction = result.next
                            allowViewer = currentInteraction !is OutputViewerInteraction && currentInteraction.allowViewerOnComplete
                            messageLines = result.lines
                            clearInput()
                            rerender()
                        }
                        is InteractionResult.Complete -> {
                            state.markInteractionIntroShown()
                            completedLines = result.lines
                            completedSaveContext = result.saveContext
                            completedDeleteContext = result.deleteContext
                            completedLoadContext = result.loadContext
                            allowViewer = currentInteraction !is OutputViewerInteraction && currentInteraction.allowViewerOnComplete
                            state.clearInteraction()
                            if (transcriptLines.isNotEmpty()) {
                                val finalLines = if (completedLines.isEmpty()) {
                                    transcriptLines
                                } else {
                                    transcriptLines + completedLines
                                }
                                finalRenderLines = addTrailingBlank(finalLines)
                                finalizeInSection = true
                                rerender()
                            } else {
                                signal()
                            }
                        }
                    }
                }

                onKeyPressed {
                    if (key == Keys.TAB) {
                        val completer = currentInteraction.inputCompleter()
                            ?: return@onKeyPressed
                        val current = getInput().orEmpty()
                        val completion = completer.complete(current)
                        if (!completion.isNullOrEmpty()) {
                            setInput(current + completion)
                        }
                        return@onKeyPressed
                    }
                    val keyResult = currentInteraction.onKeyPressed(key) ?: return@onKeyPressed
                    when (keyResult) {
                        is InteractionKeyResult.Rerender -> rerender()
                        is InteractionKeyResult.Complete -> {
                            completedLines = keyResult.lines
                            completedSaveContext = keyResult.saveContext
                            completedDeleteContext = keyResult.deleteContext
                            completedLoadContext = keyResult.loadContext
                            allowViewer = currentInteraction !is OutputViewerInteraction && currentInteraction.allowViewerOnComplete
                            state.clearInteraction()
                            if (!keyResult.skipRender && transcriptLines.isNotEmpty()) {
                                val finalLines = if (completedLines.isEmpty()) {
                                    transcriptLines
                                } else {
                                    transcriptLines + completedLines
                                }
                                finalRenderLines = addTrailingBlank(finalLines)
                                finalizeInSection = true
                                rerender()
                            } else {
                                if (!keyResult.skipRender) {
                                    rerender()
                                }
                                signal()
                            }
                        }
                    }
                }

                waitForSignal()
            }

            if (finalRenderLines != null) {
                return InteractionOutcome(
                    lines = emptyList(),
                    allowViewer = allowViewer,
                    leadingBlank = false,
                    trailingBlank = false,
                    saveContext = completedSaveContext,
                    deleteContext = completedDeleteContext,
                    loadContext = completedLoadContext,
                )
            }

            val finalLines = if (transcriptLines.isEmpty()) completedLines else transcriptLines + completedLines
            return InteractionOutcome(
                lines = finalLines,
                allowViewer = allowViewer,
                leadingBlank = transcriptLines.isEmpty(),
                trailingBlank = true,
                saveContext = completedSaveContext,
                deleteContext = completedDeleteContext,
                loadContext = completedLoadContext,
            )
        }

        // Initial banner.
        renderOutput(
            listOf(
                "Maryk CLI",
                "Type `help` to see available commands. Use Ctrl+C to exit.",
            ),
            trailingBlank = true,
        )

        while (true) {
            if (state.hasActiveInteraction()) {
                val outcome = runInteraction()
                if (outcome.lines.isNotEmpty()) {
                    if (outcome.allowViewer) {
                        val showChrome = shouldShowViewerChrome(outcome.lines)
                        state.startInteraction(
                            OutputViewerInteraction(
                                lines = outcome.lines,
                                terminalHeight = terminalHeight(),
                                saveContext = outcome.saveContext,
                                deleteContext = outcome.deleteContext,
                                loadContext = outcome.loadContext,
                                showChrome = showChrome,
                            ),
                        )
                        continue
                    }
                    renderOutput(
                        outcome.lines,
                        leadingBlank = outcome.leadingBlank,
                        trailingBlank = outcome.trailingBlank,
                    )
                }
                continue
            }

            val entered = promptForCommand()
            val trimmed = entered.trim()
            if (trimmed.isEmpty()) {
                continue
            }
            if (trimmed.equals("exit", ignoreCase = true) || trimmed.equals("quit", ignoreCase = true)) {
                break
            }

            if (commandHistory.lastOrNull() != trimmed) {
                commandHistory.add(trimmed)
            }

            val parseResult = CommandLineParser.parse(trimmed)
            val tokens = when (parseResult) {
                is CommandLineParser.ParseResult.Success -> parseResult.tokens
                is CommandLineParser.ParseResult.Error -> {
                    renderOutput(
                        listOf("Command parse error: ${parseResult.message}"),
                        leadingBlank = true,
                        trailingBlank = true,
                    )
                    continue
                }
            }
            if (tokens.isEmpty()) continue

            val commandName = tokens.first().lowercase()
            val arguments = tokens.drop(1)
            val result = registry.execute(commandName, arguments)
            if (result.shouldExit) break

            if (result.lines.isNotEmpty()) {
                val canOpenViewer = !result.isError &&
                    !state.hasActiveInteraction() &&
                    commandName !in VIEWER_EXCLUDED_COMMANDS
                if (canOpenViewer) {
                    val showChrome = shouldShowViewerChrome(result.lines)
                    state.startInteraction(
                        OutputViewerInteraction(
                            lines = result.lines,
                            terminalHeight = terminalHeight(),
                            saveContext = result.saveContext,
                            deleteContext = result.deleteContext,
                            loadContext = result.loadContext,
                            showChrome = showChrome,
                        ),
                    )
                } else {
                    renderOutput(result.lines, leadingBlank = true, trailingBlank = true)
                }
            }
        }
    }
}

private fun defaultRegistry(
    state: CliState,
    environment: CliEnvironment,
): CommandRegistry {
    return CommandRegistry(state, environment)
        .apply {
            registerAll(
                HelpCommand(),
                ConnectCommand(),
                DisconnectCommand(),
                AddCommand(),
                UndeleteCommand(),
                GetCommand(),
                ChangesCommand(),
                ScanCommand(),
                ListCommand(),
                ModelCommand(),
            )
        }
}

private fun printNonInteractiveHelp(registry: CommandRegistry) {
    println("Maryk CLI")
    println("One-shot usage: maryk --connect <store> [--dir <path>] [--cluster <file>] [--tenant <name>] --exec \"<command>\"")
    println("Type `help` to see available commands. Use Ctrl+C to exit.")
    val help = registry.execute("help", emptyList())
    help.lines.forEach { println(it) }
}

internal fun printOneShotUsage() {
    println("One-shot usage:")
    println("  maryk --connect <store> [--dir <path>] [--cluster <file>] [--tenant <name>] --exec \"<command>\"")
}

internal data class OneShotOptions(
    val store: String,
    val connectArgs: List<String>,
    val commandLine: String,
)

internal sealed class OneShotParseResult {
    data class Success(val options: OneShotOptions) : OneShotParseResult()
    data class Error(val message: String) : OneShotParseResult()
}

internal fun parseOneShotArgs(args: Array<String>): OneShotParseResult? {
    if (args.isEmpty()) return null
    if (!args.contains("--exec")) return null

    var store: String? = null
    val connectArgs = mutableListOf<String>()
    var exec: String? = null
    var collectingConnect = false
    var index = 0

    while (index < args.size) {
        val token = args[index]
        when (token) {
            "--connect" -> {
                if (store != null) {
                    return OneShotParseResult.Error("`--connect` can only be specified once.")
                }
                val value = args.getOrNull(index + 1)
                    ?: return OneShotParseResult.Error("`--connect` requires a store type.")
                store = value
                collectingConnect = true
                index += 1
            }
            "--exec" -> {
                if (exec != null) {
                    return OneShotParseResult.Error("`--exec` can only be specified once.")
                }
                val value = args.getOrNull(index + 1)
                    ?: return OneShotParseResult.Error("`--exec` requires a command string.")
                exec = value
                collectingConnect = false
                index += 1
            }
            else -> {
                if (collectingConnect) {
                    connectArgs += token
                } else {
                    return OneShotParseResult.Error("Unknown option: $token")
                }
            }
        }
        index += 1
    }

    if (store == null) {
        return OneShotParseResult.Error("`--connect` is required for one-shot mode.")
    }
    if (exec == null) {
        return OneShotParseResult.Error("`--exec` is required for one-shot mode.")
    }

    return OneShotParseResult.Success(
        OneShotOptions(
            store = store,
            connectArgs = connectArgs,
            commandLine = exec,
        )
    )
}

internal fun runOneShot(
    registry: CommandRegistry,
    options: OneShotOptions,
): Int {
    val state = registry.state
    val previousOneShot = state.isOneShotMode
    state.isOneShotMode = true
    return try {
        val connectionResult = registry.execute(
            "connect",
            listOf(options.store) + options.connectArgs,
        )
        if (connectionResult.isError) {
            connectionResult.lines.forEach(::println)
            return 1
        }
        if (state.hasActiveInteraction()) {
            state.clearInteraction()
            println("Connect requires interactive mode. Run without --exec to use it.")
            return 1
        }

        val parsed = CommandLineParser.parse(options.commandLine)
        val tokens = when (parsed) {
            is CommandLineParser.ParseResult.Error -> {
                println("Command parse error: ${parsed.message}")
                return 1
            }
            is CommandLineParser.ParseResult.Success -> parsed.tokens
        }

        if (tokens.isEmpty()) {
            println("No command provided for --exec.")
            return 1
        }

        val commandName = tokens.first()
        val result = registry.execute(commandName, tokens.drop(1))
        result.lines.forEach(::println)

        if (state.hasActiveInteraction()) {
            state.clearInteraction()
            println("Command `$commandName` requires interactive mode. Run without --exec to use it.")
            return 1
        }

        if (result.isError) 1 else 0
    } finally {
        state.isOneShotMode = previousOneShot
        state.clearConnection()?.close()
    }
}

private val EXIT_TOKENS = setOf("q", "quit", "exit")
private val VIEWER_EXCLUDED_COMMANDS = setOf("add", "changes", "connect", "help", "list", "undelete")

private fun addTrailingBlank(lines: List<String>): List<String> {
    if (lines.isEmpty()) return lines
    return if (lines.last().isEmpty()) lines else lines + ""
}

private fun shouldShowViewerChrome(lines: List<String>): Boolean {
    if (lines.isEmpty()) return false
    val available = (terminalHeight() - 1).coerceAtLeast(1)
    return lines.size + 1 > available
}
