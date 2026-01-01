package io.maryk.cli

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
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
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
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

internal data class SaveOptions(
    val directory: String,
    val format: SaveFormat,
    val includeMeta: Boolean,
    val packageName: String?,
    val noDeps: Boolean,
)

internal sealed class SaveOptionsResult {
    data class Success(val options: SaveOptions) : SaveOptionsResult()
    data class Error(val message: String) : SaveOptionsResult()
}

internal data class LoadOptions(
    val path: String,
    val format: SaveFormat,
    val ifVersion: ULong?,
    val useMeta: Boolean,
)

internal sealed class LoadOptionsResult {
    data class Success(val options: LoadOptions) : LoadOptionsResult()
    data class Error(val message: String) : LoadOptionsResult()
}

internal data class InlineOptions(
    val reference: String,
    val value: String?,
    val ifVersion: ULong?,
)

internal sealed class InlineOptionsResult {
    data class Success(val options: InlineOptions) : InlineOptionsResult()
    data class Error(val message: String) : InlineOptionsResult()
}

internal data class DeleteOptions(
    val hardDelete: Boolean,
)

internal sealed class DeleteOptionsResult {
    data class Success(val options: DeleteOptions) : DeleteOptionsResult()
    data class Error(val message: String) : DeleteOptionsResult()
}

internal fun parseSaveOptions(tokens: List<String>, saveContext: SaveContext?): SaveOptionsResult {
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

internal fun parseLoadOptions(tokens: List<String>): LoadOptionsResult {
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

internal fun parseInlineOptions(tokens: List<String>, requiresValue: Boolean): InlineOptionsResult {
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

internal fun applySet(loadContext: LoadContext, options: InlineOptions): ApplyResult {
    val rawValue = options.value ?: return ApplyResult("Set requires a value.", success = false)
    val reference = loadContext.resolveReference(options.reference)
    val value = loadContext.parseValueForReference(reference, rawValue)
    val change = Change(createReferencePair(reference, value))
    return loadContext.applyChangesResult(listOf(change), ifVersion = options.ifVersion)
}

internal fun applyUnset(loadContext: LoadContext, options: InlineOptions): ApplyResult {
    val reference = loadContext.resolveReference(options.reference)
    val change = Change(createReferencePair(reference, null))
    return loadContext.applyChangesResult(listOf(change), ifVersion = options.ifVersion)
}

internal fun applyAppend(loadContext: LoadContext, options: InlineOptions): ApplyResult {
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

internal fun applyRemove(loadContext: LoadContext, options: InlineOptions): ApplyResult {
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

internal fun parseDeleteOptions(tokens: List<String>): DeleteOptionsResult {
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

private fun selectFormat(current: SaveFormat?, next: SaveFormat): SaveFormat? {
    return if (current != null && current != next) null else next
}

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
