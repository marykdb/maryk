package io.maryk.cli.commands

import io.maryk.cli.CliInteraction
import io.maryk.cli.InteractionResult
import io.maryk.cli.OptionSelectorInteraction
import io.maryk.cli.OptionSelectorInteraction.Option
import io.maryk.cli.OptionSelectorInteraction.Selection
import io.maryk.cli.KotlinSaveResult
import io.maryk.cli.SaveContext
import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.protobuf.WriteCache
import maryk.json.JsonWriter
import maryk.generator.kotlin.generateKotlin
import maryk.yaml.YamlWriter

class ModelCommand : Command {
    override val name: String = "model"
    override val description: String = "Show a model definition as YAML."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        if (arguments.any { it == "--help" || it == "-h" || it.equals("help", ignoreCase = true) }) {
            return CommandResult(lines = usageLines())
        }

        val parsed = parseArguments(arguments)
        if (parsed is ParseArgumentsResult.Error) {
            return CommandResult(lines = parsed.lines, isError = true)
        }
        val options = (parsed as ParseArgumentsResult.Success).options
        val query = parsed.query

        val connection = context.state.currentConnection
            ?: return CommandResult(
                lines = listOf("Not connected to any store. Use `connect` first."),
                isError = true,
            )

        val modelsById = connection.dataStore.dataModelsById
        if (modelsById.isEmpty()) {
            return CommandResult(lines = listOf("No models found in the connected store."))
        }

        if (options.allModels) {
            val rendered = renderAllModels(modelsById, options.includeDependencies)
            return CommandResult(lines = rendered.lines, saveContext = rendered.saveContext)
        }

        if (query.isBlank()) {
            context.state.startInteraction(
                ModelSelectionInteraction(
                    modelsById = modelsById,
                    includeDependencies = options.includeDependencies,
                )
            )
            return CommandResult(lines = emptyList())
        }

        return when (val resolved = resolveModel(modelsById, query)) {
            is ResolveResult.Found -> {
                val rendered = renderModelDefinition(resolved.model, options.includeDependencies)
                CommandResult(lines = rendered.lines, saveContext = rendered.saveContext)
            }
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
        private val includeDependencies: Boolean,
    ) : CliInteraction by OptionSelectorInteraction(
        options = modelsById.entries
            .sortedBy { it.value.Meta.name.lowercase() }
            .map { (id, model) ->
                Option(model, "${model.Meta.name} (id=$id)")
            },
        promptLabel = "model> ",
        introLines = listOf("Select a model (use up/down arrows and press Enter):"),
        onSelection = { option ->
            val rendered = renderModelDefinition(option.value, includeDependencies)
            InteractionResult.Complete(rendered.lines, saveContext = rendered.saveContext)
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
        "  model [--with-deps] [<name|id>]",
        "  model --all [--with-deps]",
        "Examples:",
        "  model",
        "  model Person",
        "  model 1",
        "  model --with-deps Person",
        "  model --all --with-deps",
    )

    private data class ModelOptions(
        val includeDependencies: Boolean,
        val allModels: Boolean,
    )

    private sealed class ParseArgumentsResult {
        data class Success(val options: ModelOptions, val query: String) : ParseArgumentsResult()
        data class Error(val lines: List<String>) : ParseArgumentsResult()
    }

    private fun parseArguments(arguments: List<String>): ParseArgumentsResult {
        if (arguments.isEmpty()) {
            return ParseArgumentsResult.Success(ModelOptions(includeDependencies = false, allModels = false), "")
        }

        val queryTokens = mutableListOf<String>()
        var includeDependencies = false
        var allModels = false

        arguments.forEach { token ->
            when (token.lowercase()) {
                "--with-deps", "--with-dependencies", "--deps", "--full" -> includeDependencies = true
                "--all", "--all" -> allModels = true
                else -> {
                    if (token.startsWith("-")) {
                        return ParseArgumentsResult.Error(
                            listOf(
                                "Unknown option `$token`.",
                                "Use `model --help` to see available options.",
                            )
                        )
                    }
                    queryTokens.add(token)
                }
            }
        }

        val query = queryTokens.joinToString(separator = " ").trim()
        if (allModels && query.isNotEmpty()) {
            return ParseArgumentsResult.Error(
                listOf(
                    "`--all` cannot be combined with a model name or id.",
                    "Usage: model --all [--with-deps]",
                )
            )
        }

        return ParseArgumentsResult.Success(
            ModelOptions(includeDependencies = includeDependencies, allModels = allModels),
            query,
        )
    }
}

private data class RenderedDefinition(
    val lines: List<String>,
    val saveContext: SaveContext,
)

private fun renderModelDefinition(
    model: IsRootDataModel,
    includeDependencies: Boolean,
): RenderedDefinition {
    val rootDataModel = model as? RootDataModel<*>
        ?: return RenderedDefinition(
            lines = listOf("Model definitions can only be rendered for RootDataModel instances."),
            saveContext = SaveContext(
                key = model.Meta.name,
                dataYaml = "",
                dataJson = "",
                dataProto = ByteArray(0),
                metaYaml = "",
                metaJson = "",
                metaProto = ByteArray(0),
            ),
        )

    val displayDefinitions = buildDefinitions(rootDataModel, includeDependencies)
    val fullDefinitions = buildDefinitions(rootDataModel, includeDependencies = true)
    val noDepsDefinitions = buildDefinitions(rootDataModel, includeDependencies = false)

    val displayYaml = serializeDefinitionsToYaml(displayDefinitions)
    val displayLines = displayYaml.trimEnd().lines()

    val fullYaml = serializeDefinitionsToYaml(fullDefinitions)
    val fullJson = serializeDefinitionsToJson(fullDefinitions)
    val fullProto = serializeDefinitionsToProto(fullDefinitions)
    val noDepsYaml = serializeDefinitionsToYaml(noDepsDefinitions)
    val noDepsJson = serializeDefinitionsToJson(noDepsDefinitions)
    val noDepsProto = serializeDefinitionsToProto(noDepsDefinitions)

    val kotlinGenerator: (String) -> KotlinSaveResult = { packageName ->
        generateKotlinFiles(fullDefinitions, packageName)
    }
    val kotlinNoDepsGenerator: (String) -> KotlinSaveResult = { packageName ->
        generateKotlinFiles(noDepsDefinitions, packageName)
    }

    return RenderedDefinition(
        lines = displayLines,
        saveContext = SaveContext(
            key = rootDataModel.Meta.name,
            dataYaml = fullYaml,
            dataJson = fullJson,
            dataProto = fullProto,
            metaYaml = fullYaml,
            metaJson = fullJson,
            metaProto = fullProto,
            noDepsYaml = noDepsYaml,
            noDepsJson = noDepsJson,
            noDepsProto = noDepsProto,
            kotlinGenerator = kotlinGenerator,
            kotlinNoDepsGenerator = kotlinNoDepsGenerator,
        ),
    )
}

private fun renderAllModels(
    modelsById: Map<UInt, IsRootDataModel>,
    includeDependencies: Boolean,
): RenderedDefinition {
    val ordered = modelsById.entries.sortedBy { it.key }.map { it.value }
    val displayDefinitions = buildList<MarykPrimitive> {
        ordered.forEach { model ->
            val root = model as? RootDataModel<*> ?: return@forEach
            val definitions = buildDefinitions(root, includeDependencies = includeDependencies).definitions
            addAll(definitions)
        }
    }.distinctBy { it.Meta.primitiveType to it.Meta.name }
    val display = Definitions(displayDefinitions)

    val fullDefinitions = buildList<MarykPrimitive> {
        ordered.forEach { model ->
            val root = model as? RootDataModel<*> ?: return@forEach
            val definitions = buildDefinitions(root, includeDependencies = true).definitions
            addAll(definitions)
        }
    }.distinctBy { it.Meta.primitiveType to it.Meta.name }
    val full = Definitions(fullDefinitions)

    val noDepsDefinitions = buildList<MarykPrimitive> {
        ordered.forEach { model ->
            val root = model as? RootDataModel<*> ?: return@forEach
            val definitions = buildDefinitions(root, includeDependencies = false).definitions
            addAll(definitions)
        }
    }.distinctBy { it.Meta.primitiveType to it.Meta.name }
    val noDeps = Definitions(noDepsDefinitions)

    val yaml = serializeDefinitionsToYaml(full)
    val json = serializeDefinitionsToJson(full)
    val proto = serializeDefinitionsToProto(full)
    val noDepsYaml = serializeDefinitionsToYaml(noDeps)
    val noDepsJson = serializeDefinitionsToJson(noDeps)
    val noDepsProto = serializeDefinitionsToProto(noDeps)

    val kotlinGenerator: (String) -> KotlinSaveResult = { packageName ->
        generateKotlinFiles(full, packageName)
    }
    val kotlinNoDepsGenerator: (String) -> KotlinSaveResult = { packageName ->
        generateKotlinFiles(noDeps, packageName)
    }

    val header = listOf(
        "All models: ${ordered.size}",
        "Definitions: ${display.definitions.size}",
    )
    val lines = header + serializeDefinitionsToYaml(display).trimEnd().lines()

    return RenderedDefinition(
        lines = lines,
        saveContext = SaveContext(
            key = "models",
            dataYaml = yaml,
            dataJson = json,
            dataProto = proto,
            metaYaml = yaml,
            metaJson = json,
            metaProto = proto,
            noDepsYaml = noDepsYaml,
            noDepsJson = noDepsJson,
            noDepsProto = noDepsProto,
            kotlinGenerator = kotlinGenerator,
            kotlinNoDepsGenerator = kotlinNoDepsGenerator,
        ),
    )
}

private fun generateKotlinFiles(
    definitions: Definitions,
    packageName: String,
): KotlinSaveResult {
    val outputs = linkedMapOf<String, StringBuilder>()
    definitions.generateKotlin(packageName) { name ->
        val builder = StringBuilder()
        outputs[name] = builder
        { chunk -> builder.append(chunk) }
    }
    return KotlinSaveResult(
        files = outputs.mapKeys { "${it.key}.kt" }.mapValues { it.value.toString() },
    )
}
private fun buildDefinitions(
    rootDataModel: RootDataModel<*>,
    includeDependencies: Boolean,
): Definitions {
    val dependencies = mutableListOf<MarykPrimitive>()
    if (includeDependencies) {
        rootDataModel.getAllDependencies(dependencies)
    }

    val dedupedDependencies = dependencies
        .distinctBy { it.Meta.primitiveType to it.Meta.name }
        .filterNot { it.Meta.primitiveType == PrimitiveType.RootModel && it.Meta.name == rootDataModel.Meta.name }
        .sortedWith(compareBy({ it.Meta.primitiveType.name }, { it.Meta.name }))

    val allDefinitions = buildList {
        addAll(dedupedDependencies)
        add(rootDataModel)
    }
    return Definitions(allDefinitions)
}

private fun serializeDefinitionsToYaml(definitions: Definitions): String {
    val builder = StringBuilder()
    val writer = YamlWriter { builder.append(it) }
    val conversionContext = DefinitionsConversionContext(DefinitionsContext())
    Definitions.Serializer.writeObjectAsJson(definitions, writer, conversionContext, skip = null)
    return builder.toString().trimEnd()
}

private fun serializeDefinitionsToJson(definitions: Definitions): String {
    val builder = StringBuilder()
    val writer = JsonWriter(pretty = true) { builder.append(it) }
    val conversionContext = DefinitionsConversionContext(DefinitionsContext())
    Definitions.Serializer.writeObjectAsJson(definitions, writer, conversionContext, skip = null)
    return builder.toString().trimEnd()
}

private fun serializeDefinitionsToProto(definitions: Definitions): ByteArray {
    val conversionContext = DefinitionsConversionContext(DefinitionsContext())
    val cache = WriteCache()
    val length = Definitions.Serializer.calculateObjectProtoBufLength(definitions, cache, conversionContext)
    val bytes = ByteArray(length)
    var index = 0
    Definitions.Serializer.writeObjectProtoBuf(definitions, cache, { bytes[index++] = it }, conversionContext)
    return bytes
}
