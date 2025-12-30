package io.maryk.cli

import com.varabyte.kotter.foundation.input.InputCompleter
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys
import maryk.core.properties.IsPropertyContext
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.TypeReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.enum.TypeEnum
import maryk.core.query.changes.Change
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.IsReferenceValueOrNullPair
import maryk.core.query.pairs.with
import maryk.core.values.Values
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

    private data class SaveOptions(
        val directory: String,
        val format: SaveFormat,
        val includeMeta: Boolean,
        val packageName: String?,
        val noDeps: Boolean,
    )

    private sealed class SaveOptionsResult {
        data class Success(val options: SaveOptions) : SaveOptionsResult()
        data class Error(val message: String) : SaveOptionsResult()
    }

    private data class LoadOptions(
        val path: String,
        val format: SaveFormat,
        val ifVersion: ULong?,
        val useMeta: Boolean,
    )

    private sealed class LoadOptionsResult {
        data class Success(val options: LoadOptions) : LoadOptionsResult()
        data class Error(val message: String) : LoadOptionsResult()
    }

    private data class InlineOptions(
        val reference: String,
        val value: String?,
        val ifVersion: ULong?,
    )

    private sealed class InlineOptionsResult {
        data class Success(val options: InlineOptions) : InlineOptionsResult()
        data class Error(val message: String) : InlineOptionsResult()
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
        var packageName: String? = null
        var noDeps = false
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            val lowered = token.lowercase()
            when {
                lowered == "--meta" -> includeMeta = true
                lowered == "--yaml" -> {
                    val next = selectFormat(format, SaveFormat.YAML) ?: return SaveOptionsResult.Error(
                        "Choose only one format: --yaml, --json, --proto, or --kotlin"
                    )
                    format = next
                }
                lowered == "--json" -> {
                    val next = selectFormat(format, SaveFormat.JSON) ?: return SaveOptionsResult.Error(
                        "Choose only one format: --yaml, --json, --proto, or --kotlin"
                    )
                    format = next
                }
                lowered == "--proto" -> {
                    val next = selectFormat(format, SaveFormat.PROTO) ?: return SaveOptionsResult.Error(
                        "Choose only one format: --yaml, --json, --proto, or --kotlin"
                    )
                    format = next
                }
                lowered == "--kotlin" -> {
                    if (saveContext?.kotlinGenerator == null) {
                        return SaveOptionsResult.Error("Kotlin output not available for this data.")
                    }
                    val next = selectFormat(format, SaveFormat.KOTLIN) ?: return SaveOptionsResult.Error(
                        "Choose only one format: --yaml, --json, --proto, or --kotlin"
                    )
                    format = next
                }
                lowered == "--no-deps" -> {
                    if (saveContext?.supportsNoDeps != true) {
                        return SaveOptionsResult.Error("No-deps output not available for this data.")
                    }
                    noDeps = true
                }
                lowered.startsWith("--package=") -> {
                    packageName = token.substringAfter("=", missingDelimiterValue = "").ifBlank {
                        return SaveOptionsResult.Error("`--package` requires a value.")
                    }
                }
                lowered == "--package" -> {
                    if (index + 1 >= tokens.size) {
                        return SaveOptionsResult.Error("`--package` requires a value.")
                    }
                    packageName = tokens[index + 1]
                    index += 1
                }
                token.startsWith("--") -> {
                    return SaveOptionsResult.Error("Unknown option: $token")
                }
                directory == null -> directory = token
                else -> return SaveOptionsResult.Error("Unexpected argument: $token")
            }
            index += 1
        }

        val resolvedDir = directory ?: "./"
        val resolvedFormat = format ?: SaveFormat.YAML
        if (packageName != null && resolvedFormat != SaveFormat.KOTLIN) {
            return SaveOptionsResult.Error("`--package` is only valid with --kotlin.")
        }
        if (resolvedFormat == SaveFormat.KOTLIN && packageName.isNullOrBlank()) {
            return SaveOptionsResult.Error("`--package` is required for --kotlin.")
        }
        if (noDeps && saveContext?.supportsNoDeps != true) {
            return SaveOptionsResult.Error("No-deps output not available for this data.")
        }

        return SaveOptionsResult.Success(
            SaveOptions(
                directory = resolvedDir,
                format = resolvedFormat,
                includeMeta = includeMeta,
                packageName = packageName,
                noDeps = noDeps,
            )
        )
    }

    private fun selectFormat(current: SaveFormat?, next: SaveFormat): SaveFormat? {
        return if (current != null && current != next) null else next
    }

    private fun parseLoadOptions(tokens: List<String>): LoadOptionsResult {
        var path: String? = null
        var format: SaveFormat? = null
        var ifVersion: ULong? = null
        var useMeta = false
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            val lowered = token.lowercase()
            when {
                lowered == "--yaml" -> {
                    val next = selectFormat(format, SaveFormat.YAML) ?: return LoadOptionsResult.Error(
                        "Choose only one format: --yaml, --json, or --proto"
                    )
                    format = next
                }
                lowered == "--json" -> {
                    val next = selectFormat(format, SaveFormat.JSON) ?: return LoadOptionsResult.Error(
                        "Choose only one format: --yaml, --json, or --proto"
                    )
                    format = next
                }
                lowered == "--proto" -> {
                    val next = selectFormat(format, SaveFormat.PROTO) ?: return LoadOptionsResult.Error(
                        "Choose only one format: --yaml, --json, or --proto"
                    )
                    format = next
                }
                lowered == "--meta" -> useMeta = true
                lowered.startsWith("--if-version=") -> {
                    val value = token.substringAfter("=", missingDelimiterValue = "").ifBlank {
                        return LoadOptionsResult.Error("`--if-version` requires a value.")
                    }
                    ifVersion = value.toULongOrNull()
                        ?: return LoadOptionsResult.Error("Invalid `--if-version` value: $value")
                }
                lowered == "--if-version" -> {
                    if (index + 1 >= tokens.size) {
                        return LoadOptionsResult.Error("`--if-version` requires a value.")
                    }
                    val value = tokens[index + 1]
                    ifVersion = value.toULongOrNull()
                        ?: return LoadOptionsResult.Error("Invalid `--if-version` value: $value")
                    index += 1
                }
                lowered == "--kotlin" -> {
                    return LoadOptionsResult.Error("Kotlin input is not supported.")
                }
                token.startsWith("--") -> {
                    return LoadOptionsResult.Error("Unknown option: $token")
                }
                path == null -> path = token
                else -> return LoadOptionsResult.Error("Unexpected argument: $token")
            }
            index += 1
        }

        val resolvedPath = path ?: return LoadOptionsResult.Error("Load requires a file path.")
        val resolvedFormat = format ?: SaveFormat.YAML

        return LoadOptionsResult.Success(
            LoadOptions(
                path = resolvedPath,
                format = resolvedFormat,
                ifVersion = ifVersion,
                useMeta = useMeta,
            )
        )
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

    private fun parseInlineOptions(tokens: List<String>, requiresValue: Boolean): InlineOptionsResult {
        val positional = mutableListOf<String>()
        var ifVersion: ULong? = null
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            val lowered = token.lowercase()
            when {
                lowered.startsWith("--if-version=") -> {
                    val value = token.substringAfter("=", missingDelimiterValue = "").ifBlank {
                        return InlineOptionsResult.Error("`--if-version` requires a value.")
                    }
                    ifVersion = value.toULongOrNull()
                        ?: return InlineOptionsResult.Error("Invalid `--if-version` value: $value")
                }
                lowered == "--if-version" -> {
                    if (index + 1 >= tokens.size) {
                        return InlineOptionsResult.Error("`--if-version` requires a value.")
                    }
                    val value = tokens[index + 1]
                    ifVersion = value.toULongOrNull()
                        ?: return InlineOptionsResult.Error("Invalid `--if-version` value: $value")
                    index += 1
                }
                token.startsWith("--") -> {
                    return InlineOptionsResult.Error("Unknown option: $token")
                }
                else -> positional.add(token)
            }
            index += 1
        }

        if (positional.isEmpty()) {
            return InlineOptionsResult.Error("Reference path is required.")
        }
        if (requiresValue && positional.size < 2) {
            return InlineOptionsResult.Error("Value is required.")
        }

        val reference = positional.first()
        val value = if (requiresValue) {
            positional.drop(1).joinToString(" ")
        } else {
            null
        }

        return InlineOptionsResult.Success(
            InlineOptions(
                reference = reference,
                value = value,
                ifVersion = ifVersion,
            )
        )
    }

    private fun applySet(loadContext: LoadContext, options: InlineOptions): ApplyResult {
        val rawValue = options.value ?: return ApplyResult("Set requires a value.", success = false)
        val reference = loadContext.resolveReference(options.reference)
        val value = loadContext.parseValueForReference(reference, rawValue)
        val change = Change(createReferencePair(reference, value))
        return loadContext.applyChangesResult(listOf(change), ifVersion = options.ifVersion)
    }

    private fun applyUnset(loadContext: LoadContext, options: InlineOptions): ApplyResult {
        val reference = loadContext.resolveReference(options.reference)
        val change = Change(createReferencePair(reference, null))
        return loadContext.applyChangesResult(listOf(change), ifVersion = options.ifVersion)
    }

    private fun applyAppend(loadContext: LoadContext, options: InlineOptions): ApplyResult {
        val rawValue = options.value ?: return ApplyResult("Append requires a value.", success = false)
        val reference = loadContext.resolveReference(options.reference)
        return when (reference) {
            is ListReference<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val listRef = reference as ListReference<Any, IsPropertyContext>
                val value = loadContext.parseValueForDefinition(
                    listRef.propertyDefinition.definition.valueDefinition,
                    rawValue,
                    listRef,
                )
                val change = ListChange(
                    listRef.change(addValuesToEnd = listOf(value))
                )
                loadContext.applyChangesResult(listOf(change), ifVersion = options.ifVersion)
            }
            is SetReference<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val setRef = reference as SetReference<Any, IsPropertyContext>
                val value = loadContext.parseValueForDefinition(
                    setRef.propertyDefinition.definition.valueDefinition,
                    rawValue,
                    setRef,
                )
                val change = SetChange(
                    setRef.change(addValues = setOf(value))
                )
                loadContext.applyChangesResult(listOf(change), ifVersion = options.ifVersion)
            }
            else -> ApplyResult("Append only supports list or set references.", success = false)
        }
    }

    private fun applyRemove(loadContext: LoadContext, options: InlineOptions): ApplyResult {
        val rawValue = options.value ?: return ApplyResult("Remove requires a value.", success = false)
        val reference = loadContext.resolveReference(options.reference)
        return when (reference) {
            is ListReference<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val listRef = reference as ListReference<Any, IsPropertyContext>
                val value = loadContext.parseValueForDefinition(
                    listRef.propertyDefinition.definition.valueDefinition,
                    rawValue,
                    listRef,
                )
                val change = ListChange(
                    listRef.change(deleteValues = listOf(value))
                )
                loadContext.applyChangesResult(listOf(change), ifVersion = options.ifVersion)
            }
            is SetReference<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val setRef = reference as SetReference<Any, IsPropertyContext>
                val value = loadContext.parseValueForDefinition(
                    setRef.propertyDefinition.definition.valueDefinition,
                    rawValue,
                    setRef,
                )
                val itemRef = setRef.propertyDefinition.definition.itemRef(value, setRef)
                val change = Change(createReferencePair(itemRef, null))
                loadContext.applyChangesResult(listOf(change), ifVersion = options.ifVersion)
            }
            else -> ApplyResult("Remove only supports list or set references.", success = false)
        }
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

    @Suppress("UNCHECKED_CAST")
    private fun createReferencePair(
        reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
        value: Any?,
    ): IsReferenceValueOrNullPair<Any> {
        return when (reference) {
            is ListItemReference<*, *> ->
                reference.with(value)
            is MapValueReference<*, *, *> ->
                reference.with(value)
            is SetItemReference<*, *> ->
                reference.with(value)
            is TypeReference<*, *, *> ->
                (reference as TypeReference<TypeEnum<Any>, Any, IsPropertyContext>).with(value as TypeEnum<Any>?)
            else -> {
                val definition = reference.propertyDefinition
                when (definition) {
                    is IsValueDefinitionWrapper<*, *, *, *> -> {
                        val typedRef =
                            reference as IsPropertyReference<Any, IsValueDefinitionWrapper<Any, *, IsPropertyContext, *>, *>
                        typedRef with value
                    }
                    is IsListDefinition<*, *> -> {
                        val typedRef =
                            reference as IsPropertyReference<List<Any>, IsListDefinition<Any, IsPropertyContext>, *>
                        val listValue = when (value) {
                            null -> null
                            is List<*> -> value as List<Any>
                            else -> throw IllegalArgumentException("Expected list value for ${reference.completeName}.")
                        }
                        typedRef with listValue
                    }
                    is IsSetDefinition<*, *> -> {
                        val typedRef =
                            reference as IsPropertyReference<Set<Any>, IsSetDefinition<Any, IsPropertyContext>, *>
                        val setValue = when (value) {
                            null -> null
                            is Set<*> -> value as Set<Any>
                            else -> throw IllegalArgumentException("Expected set value for ${reference.completeName}.")
                        }
                        typedRef with setValue
                    }
                    is IsMapDefinition<*, *, *> -> {
                        val typedRef =
                            reference as IsPropertyReference<Map<Any, Any>, IsMapDefinition<Any, Any, IsPropertyContext>, *>
                        val mapValue = when (value) {
                            null -> null
                            is Map<*, *> -> value as Map<Any, Any>
                            else -> throw IllegalArgumentException("Expected map value for ${reference.completeName}.")
                        }
                        typedRef with mapValue
                    }
                    is IsMultiTypeDefinition<*, *, *> -> {
                        val typedRef =
                            reference as IsPropertyReference<
                                TypedValue<TypeEnum<Any>, Any>,
                                IsMultiTypeDefinition<TypeEnum<Any>, Any, IsPropertyContext>,
                                *
                            >
                        val typedValue = when (value) {
                            null -> null
                            is TypedValue<*, *> -> value
                            else -> throw IllegalArgumentException("Expected typed value for ${reference.completeName}.")
                        }
                        typedRef with typedValue
                    }
                    is IsEmbeddedValuesDefinition<*, *> -> {
                        val typedRef =
                            reference as IsPropertyReference<
                                Values<IsValuesDataModel>,
                                IsEmbeddedValuesDefinition<IsValuesDataModel, IsPropertyContext>,
                                *
                            >
                        val embeddedValues = when (value) {
                            null -> null
                            is Values<*> -> value as Values<IsValuesDataModel>
                            else -> throw IllegalArgumentException("Expected embedded values for ${reference.completeName}.")
                        }
                        typedRef with embeddedValues
                    }
                    else -> throw IllegalArgumentException("Unsupported reference type for set/unset.")
                }
            }
        } as IsReferenceValueOrNullPair<Any>
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
