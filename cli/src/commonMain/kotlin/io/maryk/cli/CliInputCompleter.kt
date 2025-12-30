package io.maryk.cli

import com.varabyte.kotter.foundation.input.InputCompleter
import io.maryk.cli.commands.CommandRegistry
import maryk.core.models.IsRootDataModel

class CliInputCompleter(
    private val registry: CommandRegistry,
) : InputCompleter {
    override fun complete(input: String): String? {
        val trimmed = input.trimStart()
        if (trimmed.isEmpty()) {
            return null
        }
        val endsWithSpace = input.lastOrNull()?.isWhitespace() == true
        val tokens = if (trimmed.isEmpty()) {
            emptyList()
        } else {
            trimmed.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        }
        val currentToken = if (endsWithSpace) "" else tokens.lastOrNull().orEmpty()

        val commandNames = registry.list()
            .map { it.name }
            .sorted()

        if (tokens.isEmpty()) {
            return completeToken(currentToken, commandNames)
        }

        val command = tokens.first().lowercase()
        if (tokens.size == 1 && !endsWithSpace) {
            return completeToken(currentToken, commandNames)
        }

        return when (command) {
            "get", "model" -> completeModelToken(tokens, endsWithSpace, currentToken)
            "scan" -> completeScanToken(tokens, endsWithSpace, currentToken)
            "connect" -> completeConnectToken(tokens, endsWithSpace, currentToken)
            else -> null
        }
    }

    private fun completeModelToken(tokens: List<String>, endsWithSpace: Boolean, currentToken: String): String? {
        val options = listOf("--with-deps", "--all", "--key-index-format")
        if (currentToken.startsWith("--")) {
            return completeToken(currentToken, options)
        }
        if (tokens.size == 1) {
            return if (endsWithSpace) {
                completeToken("", modelNames())
            } else {
                null
            }
        }
        if (tokens.size == 2 && !endsWithSpace) {
            return completeToken(currentToken, modelNames())
        }
        return null
    }

    private fun completeConnectToken(tokens: List<String>, endsWithSpace: Boolean, currentToken: String): String? {
        val storeTypes = listOf("rocksdb", "foundationdb")
        val options = listOf("--dir", "--cluster", "--tenant")

        if (tokens.size == 1 && endsWithSpace) {
            return completeToken("", storeTypes)
        }
        if (tokens.size == 2 && !endsWithSpace) {
            return completeToken(currentToken, storeTypes)
        }
        if (currentToken.startsWith("--")) {
            return completeToken(currentToken, options)
        }
        if (endsWithSpace) {
            return completeToken("", options)
        }
        return null
    }

    private fun completeScanToken(tokens: List<String>, endsWithSpace: Boolean, currentToken: String): String? {
        val options = listOf(
            "--start-key",
            "--limit",
            "--include-start",
            "--exclude-start",
            "--to-version",
            "--include-deleted",
            "--where",
            "--order",
            "--select",
            "--show",
            "--max-chars",
        )

        if (tokens.size == 1) {
            return if (endsWithSpace) {
                completeToken("", modelNames())
            } else {
                null
            }
        }
        if (tokens.size == 2 && !endsWithSpace && !currentToken.startsWith("--")) {
            return completeToken(currentToken, modelNames())
        }

        if (currentToken.startsWith("--")) {
            return completeToken(currentToken, options)
        }

        val lowered = currentToken.lowercase()
        if (lowered.startsWith("--show=") || lowered.startsWith("--select=") || lowered.startsWith("--order=")) {
            val dataModel = resolveDataModel(tokens[1])
            val referencePaths = dataModel?.let { collectReferencePaths(it) }.orEmpty()
            val value = currentToken.substringAfter("=")
            return completeReferenceListToken(value, referencePaths)
        }

        val optionTokenIndex = if (endsWithSpace) tokens.lastIndex else tokens.size - 2
        val previousToken = tokens.getOrNull(optionTokenIndex)?.lowercase()
        if (previousToken == "--show" || previousToken == "--select" || previousToken == "--order") {
            val dataModel = resolveDataModel(tokens[1])
            val referencePaths = dataModel?.let { collectReferencePaths(it) }.orEmpty()
            return completeReferenceListToken(currentToken, referencePaths)
        }

        if (endsWithSpace) {
            return completeToken("", options)
        }
        return null
    }

    private fun modelNames(): List<String> {
        val connection = registry.state.currentConnection ?: return emptyList()
        return connection.dataStore.dataModelsById.values
            .map { it.Meta.name }
            .distinct()
            .sorted()
    }

    private fun resolveDataModel(token: String): IsRootDataModel? {
        val connection = registry.state.currentConnection ?: return null
        val dataStore = connection.dataStore
        val byName = dataStore.dataModelIdsByString[token]?.let { dataStore.dataModelsById[it] }
        if (byName != null) {
            return byName
        }
        val numericId = token.toUIntOrNull()
        return numericId?.let { dataStore.dataModelsById[it] }
    }

    private fun completeToken(current: String, candidates: List<String>): String? {
        val match = candidates.firstOrNull { it.startsWith(current, ignoreCase = true) } ?: return null
        return match.drop(current.length)
    }

    private companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
