package io.maryk.cli

import com.varabyte.kotter.foundation.input.input
import com.varabyte.kotter.foundation.input.onInputEntered
import com.varabyte.kotter.foundation.input.runUntilInputEntered
import com.varabyte.kotter.foundation.liveVarOf
import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.foundation.text.text
import com.varabyte.kotter.foundation.text.textLine
import io.maryk.cli.commands.CommandRegistry
import io.maryk.cli.commands.CommandResult
import io.maryk.cli.commands.HelpCommand

fun main() {
    val registry = defaultRegistry()
    if (!isInteractiveTerminal()) {
        printNonInteractiveHelp(registry)
        return
    }

    MarykCli(registry).run()
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

        while (true) {
            var showPrompt by liveVarOf(true)
            var commandLine by liveVarOf("")

            section {
                if (showPrompt) {
                    text(prompt)
                    input(id = "maryk-cli-input")
                }
            }.runUntilInputEntered {
                onInputEntered {
                    commandLine = input
                    showPrompt = false
                    signal()
                }
            }

            val trimmed = commandLine.trim()
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

            if (result.shouldExit) {
                break
            }
        }
    }
}

private fun defaultRegistry(): CommandRegistry {
    return CommandRegistry()
        .apply {
            register(HelpCommand())
        }
}

private fun printNonInteractiveHelp(registry: CommandRegistry) {
    println("Maryk CLI")
    println("Type `help` to see available commands. Use Ctrl+C to exit.")
    println()
    val help = registry.execute("help", emptyList())
    help.lines.forEach { println(it) }
}
