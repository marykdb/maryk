package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsSensitiveValueDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.datastore.shared.encryption.mayContainPropertyReference

/** Schema-derived prefixes; concrete collection entries are discovered from stored qualifiers. */
data class SensitiveModelReferences(
    val sensitiveReferences: List<ByteArray>,
    val sensitiveUniqueReferences: List<ByteArray>,
)

fun collectSensitiveReferences(modelId: UInt, dataModel: IsRootDataModel): SensitiveModelReferences {
    val sensitive = mutableListOf<ByteArray>()
    val unique = mutableListOf<ByteArray>()
    dataModel.forEachEmbeddedProperty { model, wrapper, reference ->
        if (wrapper is IsSensitiveValueDefinitionWrapper<*, *, *, *> && wrapper.sensitive) {
            val indexed = dataModel.Meta.keyDefinition.mayContainPropertyReference(reference) ||
                dataModel.Meta.indexes?.any { it.mayContainPropertyReference(reference) } == true
            if (indexed) {
                throw RequestException(
                    "Sensitive property ${model.Meta.name}.${wrapper.name} (modelId=$modelId) cannot be used in a key or index. Use explicit blind-index field"
                )
            }
            val bytes = reference.toStorageByteArray()
            sensitive += bytes
            if ((wrapper.definition as? IsComparableDefinition<*, *>)?.unique == true) unique += bytes
        }
    }
    return SensitiveModelReferences(sensitive, unique)
}

fun collectUniqueReferences(dataModel: IsRootDataModel): List<ByteArray> = buildList {
    dataModel.forEachEmbeddedProperty { _, wrapper, reference ->
        if ((wrapper.definition as? IsComparableDefinition<*, *>)?.unique == true) add(reference.toStorageByteArray())
    }
}

/** Retain declared order and stored descriptors which are no longer in the active schema. */
fun mergeUniqueReferences(declaredReferences: List<ByteArray>, storedReferences: List<ByteArray>): List<ByteArray> = buildList {
    addAll(declaredReferences)
    for (stored in storedReferences) if (none { it.contentEquals(stored) }) add(stored)
}

/** Track identity on the current path: reusing an embedded schema in another branch is valid. */
private fun IsValuesDataModel.forEachEmbeddedProperty(
    visit: (IsValuesDataModel, IsDefinitionWrapper<*, *, *, *>, AnyPropertyReference) -> Unit,
) {
    val path = mutableListOf<IsValuesDataModel>()
    fun walk(model: IsValuesDataModel, parent: AnyPropertyReference?) {
        if (path.any { it === model }) return
        path += model
        try {
            for (wrapper in model) {
                val reference = wrapper.ref(parent)
                visit(model, wrapper, reference)
                val definition = wrapper.definition
                if (definition is EmbeddedValuesDefinition<*>) walk(definition.dataModel, reference)
            }
        } finally {
            path.removeAt(path.lastIndex)
        }
    }
    walk(this, null)
}
