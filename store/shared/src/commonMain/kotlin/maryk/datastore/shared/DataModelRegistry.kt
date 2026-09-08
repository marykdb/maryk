package maryk.datastore.shared

import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel

internal data class DataModelRegistry(
    val dataModelsById: Map<UInt, IsRootDataModel>,
    val dataModelIdsByString: Map<String, UInt>,
)

internal fun DataModelRegistry.getDataModelId(dataModel: IsRootDataModel): UInt {
    val dataModelId = dataModelIdsByString[dataModel.Meta.name]
        ?: throw StorageException("DataStore not found ${dataModel.Meta.name}")
    val registeredModel = dataModelsById.getValue(dataModelId)
    if (registeredModel !== dataModel) {
        throw StorageException("DataStore model ${dataModel.Meta.name} does not match the registered definition")
    }
    return dataModelId
}

internal fun validatedDataModelRegistry(
    suppliedDataModelsById: Map<UInt, IsRootDataModel>,
): DataModelRegistry {
    val dataModelsById = suppliedDataModelsById.entries.associate { (id, model) -> id to model }

    if (0u in dataModelsById) {
        throw StorageException("Model ID 0 is reserved for metadata and cannot be used")
    }

    dataModelsById.forEach { (id, model) ->
        if (model.Meta.name.isBlank()) {
            throw StorageException("Data model with ID $id has a blank name")
        }
        model.checkModel()
    }

    val duplicateNames = dataModelsById.entries
        .groupBy(keySelector = { it.value.Meta.name }, valueTransform = { it.key })
        .filterValues { it.size > 1 }
    if (duplicateNames.isNotEmpty()) {
        val conflicts = duplicateNames.entries
            .sortedBy { it.key }
            .joinToString("; ") { (name, ids) ->
                "$name uses IDs ${ids.sorted().joinToString()}"
            }
        throw StorageException("Duplicate data model names: $conflicts")
    }

    return DataModelRegistry(
        dataModelsById = dataModelsById,
        dataModelIdsByString = dataModelsById.entries.associate { (id, model) -> model.Meta.name to id },
    )
}
