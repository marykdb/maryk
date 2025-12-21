package io.maryk.cli.commands

import io.maryk.cli.CliInteraction
import io.maryk.cli.InteractionResult
import io.maryk.cli.OptionSelectorInteraction
import io.maryk.cli.OptionSelectorInteraction.Option
import io.maryk.cli.OptionSelectorInteraction.Selection
import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.yaml.YamlWriter

class ModelCommand : Command {
    override val name: String = "model"
    override val description: String = "Show a model definition as YAML."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        if (arguments.any { it == "--help" || it == "-h" || it.equals("help", ignoreCase = true) }) {
            return CommandResult(lines = usageLines())
        }

        val connection = context.state.currentConnection
            ?: return CommandResult(
                lines = listOf("Not connected to any store. Use `connect` first."),
                isError = true,
            )

        val modelsById = connection.dataStore.dataModelsById
        if (modelsById.isEmpty()) {
            return CommandResult(lines = listOf("No models found in the connected store."))
        }

        if (arguments.isEmpty()) {
            context.state.startInteraction(ModelSelectionInteraction(modelsById))
            return CommandResult(
                lines = listOf("Select a model to view its definition."),
            )
        }

        val query = arguments.joinToString(separator = " ").trim()
        return when (val resolved = resolveModel(modelsById, query)) {
            is ResolveResult.Found -> CommandResult(lines = resolved.model.toDefinitionYamlLines())
            is ResolveResult.Error -> CommandResult(lines = resolved.lines, isError = true)
        }
    }

    private sealed class ResolveResult {
        data class Found(val model: IsRootDataModel) : ResolveResult()
        data class Error(val lines: List<String>) : ResolveResult()
    }

    private fun resolveModel(modelsById: Map<UInt, IsRootDataModel>, query: String): ResolveResult {
        if (query.isBlank()) {
            return ResolveResult.Error(usageLines())
        }

        val byId = query.toUIntOrNull()?.let { modelsById[it] }
        if (byId != null) return ResolveResult.Found(byId)

        val allModels = modelsById.values

        val exact = allModels.firstOrNull { it.Meta.name == query }
        if (exact != null) return ResolveResult.Found(exact)

        val exactIgnoreCase = allModels.firstOrNull { it.Meta.name.equals(query, ignoreCase = true) }
        if (exactIgnoreCase != null) return ResolveResult.Found(exactIgnoreCase)

        val prefixMatches = allModels.filter { it.Meta.name.startsWith(query, ignoreCase = true) }
        if (prefixMatches.size == 1) return ResolveResult.Found(prefixMatches.first())
        if (prefixMatches.size > 1) {
            val suggestions = prefixMatches
                .map { it.Meta.name }
                .sorted()
                .take(5)
            return ResolveResult.Error(
                buildList {
                    add("Model `$query` is ambiguous (${prefixMatches.size} matches).")
                    add("Be more specific or pick one of:")
                    suggestions.forEach { add("  - $it") }
                },
            )
        }

        val suggestions = allModels
            .map { it.Meta.name }
            .filter { it.contains(query, ignoreCase = true) }
            .sorted()
            .take(5)

        return ResolveResult.Error(
            buildList {
                add("Model `$query` not found in the connected store.")
                if (suggestions.isNotEmpty()) {
                    add("Did you mean:")
                    suggestions.forEach { add("  - $it") }
                }
                add("Run `list` to see available models.")
            },
        )
    }

    private class ModelSelectionInteraction(
        private val modelsById: Map<UInt, IsRootDataModel>,
    ) : CliInteraction by OptionSelectorInteraction(
        options = modelsById.entries
            .sortedBy { it.value.Meta.name.lowercase() }
            .map { (id, model) ->
                Option(model, "${model.Meta.name} (id=$id)")
            },
        promptLabel = "model> ",
        introLines = listOf("Select a model (use up/down arrows and press Enter):"),
        onSelection = { option ->
            InteractionResult.Complete(option.value.toDefinitionYamlLines())
        },
        onCancel = { InteractionResult.Complete(listOf("Model command cancelled.")) },
        resolveSelection = { input, currentIndex, options ->
            val trimmed = input.trim()
            when {
                trimmed.isEmpty() -> Selection.Select(currentIndex)
                trimmed.equals("cancel", ignoreCase = true) -> Selection.Cancel()
                trimmed.toIntOrNull() != null -> {
                    val index = trimmed.toInt()
                    val zeroBased = index - 1
                    if (zeroBased in options.indices) Selection.Select(zeroBased)
                    else Selection.Error("Option $index is out of range.")
                }
                else -> {
                    val matchIndex = options.indexOfFirst { it.value.Meta.name.equals(trimmed, ignoreCase = true) }
                    if (matchIndex >= 0) Selection.Select(matchIndex)
                    else Selection.Error("Unknown model `$trimmed`. Choose a listed model or type its name.")
                }
            }
        },
    )

    private fun usageLines(): List<String> = listOf(
        "Usage:",
        "  model",
        "  model <name>",
        "  model <id>",
        "Examples:",
        "  model",
        "  model Person",
        "  model 1",
    )
}

internal fun IsRootDataModel.toDefinitionYamlLines(): List<String> {
    val rootDataModel = this as? RootDataModel<*>
        ?: return listOf("Model definitions can only be rendered for RootDataModel instances.")

    val dependencies = mutableListOf<MarykPrimitive>()
    rootDataModel.getAllDependencies(dependencies)

    val dedupedDependencies = dependencies
        .distinctBy { it.Meta.primitiveType to it.Meta.name }
        .filterNot { it.Meta.primitiveType == PrimitiveType.RootModel && it.Meta.name == rootDataModel.Meta.name }
        .sortedWith(compareBy({ it.Meta.primitiveType.name }, { it.Meta.name }))

    val builder = StringBuilder()
    val writer = YamlWriter { builder.append(it) }
    val definitionsContext = DefinitionsContext()
    val conversionContext = DefinitionsConversionContext(definitionsContext)
    val allDefinitions = buildList {
        addAll(dedupedDependencies)
        add(rootDataModel)
    }
    val definitions = Definitions(allDefinitions)
    Definitions.Serializer.writeObjectAsJson(definitions, writer, conversionContext, skip = null)
    return builder.toString().trimEnd().lines()
}
