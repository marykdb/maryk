package io.maryk.cli

import com.varabyte.kotter.foundation.input.input
import com.varabyte.kotter.foundation.input.onInputEntered
import com.varabyte.kotter.foundation.input.onKeyPressed
import com.varabyte.kotter.foundation.input.runUntilInputEntered
import com.varabyte.kotter.foundation.liveVarOf
import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.foundation.text.text
import com.varabyte.kotter.foundation.text.textLine
import io.maryk.cli.commands.CommandRegistry
import io.maryk.cli.commands.CommandResult
import io.maryk.cli.commands.ConnectCommand
import io.maryk.cli.commands.DisconnectCommand
import io.maryk.cli.commands.HelpCommand
import io.maryk.cli.commands.ListCommand
import io.maryk.cli.commands.ModelCommand
import io.maryk.cli.commands.registerAll

fun main() {
    runCatching {
        val state = CliState()
        val environment = BasicCliEnvironment
        val registry = defaultRegistry(state, environment)
        if (!isInteractiveTerminal()) {
            printNonInteractiveHelp(registry)
            return
        }

        MarykCli(registry).run()
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
        val renderOutput: (String, List<String>) -> Unit = { input, lines ->
            section {
                textLine("$prompt$input")
                if (lines.isEmpty()) {
                    textLine()
                } else {
                    lines.forEach { line ->
                        textLine(line)
                    }
                }
            }.run()
        }

        section {
            textLine("Maryk CLI")
            textLine("Type `help` to see available commands. Use Ctrl+C to exit.")
            textLine()
        }.run()

        var hasRenderedOutput = false

        while (true) {
            var showPrompt by liveVarOf(true)
            var commandLine by liveVarOf("")
            val interaction = registry.state.currentInteraction
            val promptLabel = interaction?.promptLabel ?: prompt
            val inputId = if (interaction != null) "maryk-cli-interaction" else "maryk-cli-input"
            var introDisplayedThisIteration = false

            section {
                if (showPrompt) {
                    if (hasRenderedOutput) {
                        textLine()
                    }
                    val currentInteraction = registry.state.currentInteraction
                    val shouldShowIntro = currentInteraction != null && !registry.state.interactionIntroShown()
                    val introLines = if (shouldShowIntro) currentInteraction.introLines else emptyList()
                    if (introLines.isNotEmpty()) {
                        introLines.forEach { line -> textLine(line) }
                        introDisplayedThisIteration = true
                    }
                    val promptLines = currentInteraction?.promptLines() ?: emptyList()
                    promptLines.forEach { line -> textLine(line) }
                    text(promptLabel)
                    input(id = inputId)
                }
            }.runUntilInputEntered {
                onInputEntered {
                    commandLine = input
                    showPrompt = false
                    signal()
                }

                onKeyPressed {
                    val currentInteraction = registry.state.currentInteraction
                    val keyResult = currentInteraction?.onKeyPressed(key)
                    if (keyResult is InteractionKeyResult.Rerender) {
                        rerender()
                    }
                }
            }

            if (introDisplayedThisIteration) {
                registry.state.markInteractionIntroShown()
            }

            val trimmed = commandLine.trim()

            if (interaction != null) {
                val outcome = interaction.onInput(trimmed)
                val linesToShow = when (outcome) {
                    is InteractionResult.Continue -> outcome.lines
                    is InteractionResult.Stay -> outcome.lines
                    is InteractionResult.Complete -> outcome.lines
                }

                section {
                    textLine("$promptLabel$trimmed")
                    if (linesToShow.isEmpty()) {
                        textLine()
                    } else {
                        linesToShow.forEach { line ->
                            textLine(line)
                        }
                    }
                }.run()

                when (outcome) {
                    is InteractionResult.Continue -> registry.state.replaceInteraction(
                        outcome.next,
                        outcome.showIntro,
                    )
                    is InteractionResult.Stay -> Unit
                    is InteractionResult.Complete -> registry.state.clearInteraction()
                }

                hasRenderedOutput = true
                continue
            }

            if (trimmed.isEmpty()) {
                continue
            }

            val tokens = CommandLineParser.parse(trimmed)
            val parsedTokens = when (val result = tokens) {
                is CommandLineParser.ParseResult.Success -> result.tokens
                is CommandLineParser.ParseResult.Error -> {
                    renderOutput(trimmed, listOf("Parse error: ${result.message}"))
                    continue
                }
            }

            if (parsedTokens.isEmpty()) {
                continue
            }

            val commandName = parsedTokens.first()
            val arguments = if (parsedTokens.size > 1) parsedTokens.drop(1) else emptyList()

            val result = try {
                registry.execute(commandName, arguments)
            } catch (e: Exception) {
                CommandResult(
                    lines = listOf(
                        "Error executing `$commandName`: ${e.message ?: e::class.simpleName}",
                        "Run `help` for available commands."
                    ),
                    isError = true
                )
            }

            renderOutput(trimmed, result.lines)

            hasRenderedOutput = true

            if (result.shouldExit) {
                break
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
                ListCommand(),
                ModelCommand(),
            )
        }
}

private fun printNonInteractiveHelp(registry: CommandRegistry) {
    println("Maryk CLI")
    println("Type `help` to see available commands. Use Ctrl+C to exit.")
    val help = registry.execute("help", emptyList())
    help.lines.forEach { println(it) }
}
