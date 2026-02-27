package maryk.core.models.migration

import maryk.core.definitions.MarykPrimitive
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.IsDefinitionWithDataModel
import maryk.core.properties.definitions.IsReferenceDefinition

private data class ModelSortContext(
    val idsByName: Map<String, UInt>,
    val namesById: Map<UInt, String>,
)

private fun modelIdComparator(context: ModelSortContext): Comparator<UInt> =
    compareBy<UInt> { context.namesById[it].orEmpty() }.thenBy { it }

private fun collectMigrationDependenciesByModelId(
    dataModelsById: Map<UInt, IsRootDataModel>,
    context: ModelSortContext
): Map<UInt, Set<UInt>> {
    return dataModelsById.mapValues { (modelId, dataModel) ->
        val dependencyNames = linkedSetOf<String>()

        val dependencies = mutableListOf<MarykPrimitive>()
        dataModel.getAllDependencies(dependencies)
        dependencies.forEach { dependency ->
            dependencyNames.add(dependency.Meta.name)
        }

        collectRootReferenceDependencyNames(dataModel, dependencyNames)

        dependencyNames.mapNotNullTo(linkedSetOf()) { dependencyName ->
            context.idsByName[dependencyName]
        }.filterTo(linkedSetOf()) { dependencyId ->
            dependencyId != modelId
        }
    }
}

private fun collectRootReferenceDependencyNames(
    model: IsValuesDataModel,
    dependencyNames: MutableSet<String>,
    visitedModelNames: MutableSet<String> = mutableSetOf(),
) {
    if (!visitedModelNames.add(model.Meta.name)) return

    model.forEach { property ->
        when (val definition = property.definition) {
            is IsReferenceDefinition<*, *> -> dependencyNames.add(definition.dataModel.Meta.name)
        }

        val nestedModel = (property.definition as? IsDefinitionWithDataModel<*>)?.dataModel
        if (nestedModel is IsValuesDataModel) {
            collectRootReferenceDependencyNames(nestedModel, dependencyNames, visitedModelNames)
        }
    }
}

private fun insertSorted(queue: MutableList<UInt>, value: UInt, comparator: Comparator<UInt>) {
    var index = 0
    while (index < queue.size && comparator.compare(queue[index], value) <= 0) {
        index++
    }
    queue.add(index, value)
}

private fun findCyclePath(
    dependencyIdsByModelId: Map<UInt, Set<UInt>>,
    cycleNodeIds: Set<UInt>,
): List<UInt>? {
    val visited = mutableSetOf<UInt>()
    val inStack = mutableSetOf<UInt>()
    val stack = mutableListOf<UInt>()

    fun dfs(nodeId: UInt): List<UInt>? {
        visited.add(nodeId)
        inStack.add(nodeId)
        stack.add(nodeId)

        for (dependencyId in dependencyIdsByModelId[nodeId].orEmpty()) {
            if (dependencyId !in cycleNodeIds) continue
            if (dependencyId !in visited) {
                val cycle = dfs(dependencyId)
                if (cycle != null) return cycle
            } else if (dependencyId in inStack) {
                val startIndex = stack.indexOf(dependencyId)
                if (startIndex >= 0) {
                    return stack.subList(startIndex, stack.size).toList() + dependencyId
                }
            }
        }

        stack.removeAt(stack.lastIndex)
        inStack.remove(nodeId)
        return null
    }

    for (nodeId in cycleNodeIds) {
        if (nodeId !in visited) {
            val cycle = dfs(nodeId)
            if (cycle != null) return cycle
        }
    }
    return null
}

fun orderMigrationModelIds(dataModelsById: Map<UInt, IsRootDataModel>): List<UInt> {
    val context = ModelSortContext(
        idsByName = dataModelsById.entries.associate { (id, model) -> model.Meta.name to id },
        namesById = dataModelsById.entries.associate { (id, model) -> id to model.Meta.name },
    )
    val comparator = modelIdComparator(context)
    val dependencyIdsByModelId = collectMigrationDependenciesByModelId(dataModelsById, context)
    val dependentIdsByModelId = dataModelsById.keys.associateWith { mutableSetOf<UInt>() }.toMutableMap()
    val indegreeByModelId = dataModelsById.keys.associateWith { 0 }.toMutableMap()

    dependencyIdsByModelId.forEach { (modelId, dependencyIds) ->
        indegreeByModelId[modelId] = dependencyIds.size
        dependencyIds.forEach { dependencyId ->
            dependentIdsByModelId.getOrPut(dependencyId) { mutableSetOf() }.add(modelId)
        }
    }

    val queue = indegreeByModelId.entries
        .asSequence()
        .filter { it.value == 0 }
        .map { it.key }
        .sortedWith(comparator)
        .toMutableList()

    val orderedIds = mutableListOf<UInt>()
    while (queue.isNotEmpty()) {
        val nodeId = queue.removeAt(0)
        orderedIds.add(nodeId)
        dependentIdsByModelId[nodeId].orEmpty()
            .sortedWith(comparator)
            .forEach { dependentId ->
                val updated = (indegreeByModelId[dependentId] ?: 0) - 1
                indegreeByModelId[dependentId] = updated
                if (updated == 0) {
                    insertSorted(queue, dependentId, comparator)
                }
            }
    }

    if (orderedIds.size != dataModelsById.size) {
        val cycleNodeIds = indegreeByModelId
            .filterValues { it > 0 }
            .keys
            .toSet()
        val cyclePath = findCyclePath(dependencyIdsByModelId, cycleNodeIds)
        val cycleDescription = cyclePath
            ?.joinToString(" -> ") { context.namesById[it] ?: it.toString() }
            ?: cycleNodeIds
                .sortedWith(comparator)
                .joinToString(", ") { context.namesById[it] ?: it.toString() }
        throw MigrationException("Dependency cycle detected between migration models: $cycleDescription")
    }

    return orderedIds
}
