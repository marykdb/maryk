package io.maryk.cli

import maryk.core.models.IsTypedDataModel
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.enum.IndexedEnum

internal fun collectReferencePaths(
    dataModel: IsTypedDataModel<*>,
    maxDepth: Int = 3,
): List<String> {
    val results = mutableListOf<String>()
    val recursionStack = mutableSetOf<IsTypedDataModel<*>>()

    lateinit var collect: (IsTypedDataModel<*>, String?, Int) -> Unit
    fun collectDefinitionPaths(
        definition: IsPropertyDefinition<*>,
        path: String,
        depth: Int,
    ) {
        val embedded = extractEmbeddedDefinition(definition)
        if (embedded != null) {
            collect(embedded.dataModel, path, depth)
            return
        }

        val multiTypeDefinition = extractMultiTypeDefinition(definition)
        if (multiTypeDefinition != null) {
            collectMultiTypePaths(path, depth, multiTypeDefinition, collect, results)
            return
        }

        val listDefinition = extractListDefinition(definition)
        if (listDefinition != null) {
            results.add("$path.@0")
            val wildcardPath = "$path.*"
            results.add(wildcardPath)
            val listEmbedded = extractEmbeddedDefinition(listDefinition.valueDefinition)
            if (listEmbedded != null) {
                collect(listEmbedded.dataModel, wildcardPath, depth + 1)
                return
            }
            val listMultiType = extractMultiTypeDefinition(listDefinition.valueDefinition)
            if (listMultiType != null) {
                collectMultiTypePaths(wildcardPath, depth + 1, listMultiType, collect, results)
            }
            return
        }

        val mapDefinition = extractMapDefinition(definition)
        if (mapDefinition != null) {
            val wildcardPath = "$path.*"
            results.add(wildcardPath)
            val mapEmbedded = extractEmbeddedDefinition(mapDefinition.valueDefinition)
            if (mapEmbedded != null) {
                collect(mapEmbedded.dataModel, wildcardPath, depth + 1)
                return
            }
            val mapMultiType = extractMultiTypeDefinition(mapDefinition.valueDefinition)
            if (mapMultiType != null) {
                collectMultiTypePaths(wildcardPath, depth + 1, mapMultiType, collect, results)
            }
        }
    }

    collect = collect@{ model, prefix, depth ->
        if (depth > maxDepth) return@collect
        if (!recursionStack.add(model)) return@collect
        for (wrapper in model) {
            val name = wrapper.name
            val path = if (prefix == null) name else "$prefix.$name"
            results.add(path)
            collectDefinitionPaths(wrapper.definition, path, depth + 1)
        }
        recursionStack.remove(model)
    }

    collect(dataModel, null, 0)
    return results.distinct().sorted()
}

internal fun completeReferenceListToken(
    currentToken: String,
    candidates: List<String>,
): String? {
    if (candidates.isEmpty()) return null
    val commaIndex = currentToken.lastIndexOf(',')
    val token = if (commaIndex == -1) currentToken else currentToken.substring(commaIndex + 1)
    val trimmed = token.trim()
    val core = trimmed.removePrefix("-").removePrefix("+")
    val match = candidates.firstOrNull { it.startsWith(core, ignoreCase = true) } ?: return null
    return match.drop(core.length)
}

private fun extractEmbeddedDefinition(definition: IsPropertyDefinition<*>): IsEmbeddedDefinition<*>? {
    return definition as? IsEmbeddedDefinition<*>
}

private fun extractListDefinition(definition: IsPropertyDefinition<*>): IsListDefinition<*, *>? {
    return definition as? IsListDefinition<*, *>
}

private fun extractMapDefinition(definition: IsPropertyDefinition<*>): IsMapDefinition<*, *, *>? {
    return definition as? IsMapDefinition<*, *, *>
}

private fun extractMultiTypeDefinition(definition: IsPropertyDefinition<*>): IsMultiTypeDefinition<*, *, *>? {
    return definition as? IsMultiTypeDefinition<*, *, *>
}

private fun collectMultiTypePaths(
    basePath: String,
    depth: Int,
    definition: IsMultiTypeDefinition<*, *, *>,
    collect: (IsTypedDataModel<*>, String?, Int) -> Unit,
    results: MutableList<String>,
) {
    val typeReferencePath = "$basePath.*"
    results.add(typeReferencePath)

    val typeCases = definition.typeEnum.cases()
    for (case in typeCases) {
        val typeCase = case as? IndexedEnum ?: continue
        val names = buildSet {
            add(typeCase.name)
            typeCase.alternativeNames?.let { addAll(it) }
        }
        for (name in names) {
            val typedPath = "$basePath.*$name"
            results.add(typedPath)
            results.add("$basePath.>$name")
            val subDefinition = definition.definition(typeCase.index) as? IsPropertyDefinition<*>
            val embedded = subDefinition?.let(::extractEmbeddedDefinition)
            if (embedded != null) {
                collect(embedded.dataModel, typedPath, depth + 1)
            }
        }
    }
}
