package io.maryk.cli

internal sealed class RecordSubcommandResult {
    data class Success(val lines: List<String>) : RecordSubcommandResult()
    data class Error(val message: String) : RecordSubcommandResult()
}

internal fun runRecordSubcommand(
    tokens: List<String>,
    saveContext: SaveContext?,
    loadContext: LoadContext?,
    deleteContext: DeleteContext?,
): RecordSubcommandResult {
    if (tokens.isEmpty()) {
        return RecordSubcommandResult.Error(
            "Missing subcommand. Use save, load, set, unset, append, remove, or delete."
        )
    }

    val command = tokens.first().lowercase()
    val options = tokens.drop(1)

    return when (command) {
        "save" -> {
            val resolvedSaveContext = saveContext
                ?: return RecordSubcommandResult.Error("Save not available for this record.")
            when (val parseResult = parseSaveOptions(options, resolvedSaveContext)) {
                is SaveOptionsResult.Error -> RecordSubcommandResult.Error("Save failed: ${parseResult.message}")
                is SaveOptionsResult.Success -> {
                    val saveOptions = parseResult.options
                    val message = resolvedSaveContext.save(
                        directory = saveOptions.directory,
                        format = saveOptions.format,
                        includeMeta = saveOptions.includeMeta,
                        packageName = saveOptions.packageName,
                        noDeps = saveOptions.noDeps,
                    )
                    RecordSubcommandResult.Success(listOf(message))
                }
            }
        }
        "load" -> {
            val resolvedLoadContext = loadContext
                ?: return RecordSubcommandResult.Error("Load not available for this record.")
            when (val parseResult = parseLoadOptions(options)) {
                is LoadOptionsResult.Error -> RecordSubcommandResult.Error("Load failed: ${parseResult.message}")
                is LoadOptionsResult.Success -> {
                    val loadOptions = parseResult.options
                    val result = resolvedLoadContext.loadResult(
                        path = loadOptions.path,
                        format = loadOptions.format,
                        ifVersion = loadOptions.ifVersion,
                        useMeta = loadOptions.useMeta,
                    )
                    if (result.success) {
                        RecordSubcommandResult.Success(listOf(result.message))
                    } else {
                        RecordSubcommandResult.Error(result.message)
                    }
                }
            }
        }
        "set", "unset", "append", "remove" -> {
            val resolvedLoadContext = loadContext
                ?: return RecordSubcommandResult.Error("Update not available for this record.")
            val requiresValue = command != "unset"
            when (val parseResult = parseInlineOptions(options, requiresValue)) {
                is InlineOptionsResult.Error -> RecordSubcommandResult.Error(
                    "${command.replaceFirstChar { it.uppercase() }} failed: ${parseResult.message}"
                )
                is InlineOptionsResult.Success -> {
                    val inlineOptions = parseResult.options
                    val result = try {
                        when (command) {
                            "set" -> applySet(resolvedLoadContext, inlineOptions)
                            "unset" -> applyUnset(resolvedLoadContext, inlineOptions)
                            "append" -> applyAppend(resolvedLoadContext, inlineOptions)
                            else -> applyRemove(resolvedLoadContext, inlineOptions)
                        }
                    } catch (e: Throwable) {
                        ApplyResult(
                            "${command.replaceFirstChar { it.uppercase() }} failed: ${e.message ?: e::class.simpleName}",
                            success = false,
                        )
                    }
                    if (result.success) {
                        RecordSubcommandResult.Success(listOf(result.message))
                    } else {
                        RecordSubcommandResult.Error(result.message)
                    }
                }
            }
        }
        "delete" -> {
            val resolvedDeleteContext = deleteContext
                ?: return RecordSubcommandResult.Error("Delete not available for this record.")
            when (val parseResult = parseDeleteOptions(options)) {
                is DeleteOptionsResult.Error -> RecordSubcommandResult.Error("Delete failed: ${parseResult.message}")
                is DeleteOptionsResult.Success -> {
                    val deleteOptions = parseResult.options
                    val lines = try {
                        resolvedDeleteContext.onDelete(deleteOptions.hardDelete)
                    } catch (e: Throwable) {
                        listOf("Delete failed: ${e.message ?: e::class.simpleName}")
                    }
                    RecordSubcommandResult.Success(lines)
                }
            }
        }
        else -> RecordSubcommandResult.Error("Unknown subcommand: $command")
    }
}
