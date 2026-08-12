package maryk.datastore.indexeddb

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.wrapper.IsSensitiveValueDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.datastore.shared.encryption.FieldEncryptionEnvelope
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import maryk.lib.bytes.combineToByteArray

internal data class IndexedDbSensitiveModelReferences(
    val sensitiveReferences: List<ByteArray>,
    val sensitiveUniqueReferences: List<ByteArray>,
)

internal class IndexedDbSensitiveFieldSupport(
    dataModelsById: Map<UInt, IsRootDataModel>,
    private val fieldEncryptionProvider: FieldEncryptionProvider?,
) {
    private val referencesByModelId = dataModelsById.mapValues { (modelId, model) ->
        collectSensitiveReferences(modelId, model)
    }
    private val sensitiveReferencePrefixesByModelId = referencesByModelId.mapValues { it.value.sensitiveReferences }
    private val sensitiveUniqueReferencesByModelId = referencesByModelId.mapValues { it.value.sensitiveUniqueReferences }

    init {
        if (sensitiveReferencePrefixesByModelId.values.any { it.isNotEmpty() } && fieldEncryptionProvider == null) {
            throw RequestException("Sensitive properties configured but no fieldEncryptionProvider was supplied")
        }
        if (sensitiveUniqueReferencesByModelId.values.any { it.isNotEmpty() } && fieldEncryptionProvider !is SensitiveIndexTokenProvider) {
            throw RequestException(
                "Sensitive unique properties configured but fieldEncryptionProvider does not implement SensitiveIndexTokenProvider"
            )
        }
    }

    suspend fun encryptValueIfSensitive(
        modelId: UInt,
        recordKey: ByteArray,
        reference: ByteArray,
        value: ByteArray,
    ): ByteArray {
        if (!isSensitiveReference(modelId, reference)) return value
        val provider = fieldEncryptionProvider
            ?: throw RequestException("No fieldEncryptionProvider configured for sensitive property write")
        val contextualProvider = provider as? ContextualFieldEncryptionProvider
            ?: throw RequestException("Sensitive property writes require ContextualFieldEncryptionProvider")
        return combineToByteArray(
            FieldEncryptionEnvelope.Contextual.magic,
            contextualProvider.encrypt(FieldEncryptionContext(modelId, recordKey, reference), value),
        )
    }

    suspend fun decryptValueIfNeeded(
        modelId: UInt,
        recordKey: ByteArray,
        reference: ByteArray,
        value: ByteArray,
    ): ByteArray {
        val envelope = FieldEncryptionEnvelope.from(value) ?: return value
        val provider = fieldEncryptionProvider
            ?: throw RequestException("Encrypted value encountered but no fieldEncryptionProvider configured")
        return when (envelope) {
            FieldEncryptionEnvelope.Legacy -> provider.decrypt(
                value,
                envelope.magic.size,
                value.size - envelope.magic.size,
            )
            FieldEncryptionEnvelope.Contextual -> {
                val contextualProvider = provider as? ContextualFieldEncryptionProvider
                    ?: throw RequestException("MKE2 value encountered but provider does not support contextual field encryption")
                contextualProvider.decrypt(
                    FieldEncryptionContext(modelId, recordKey, reference),
                    value,
                    envelope.magic.size,
                    value.size - envelope.magic.size,
                )
            }
        }
    }

    suspend fun mapUniqueValueBytes(modelId: UInt, reference: ByteArray, value: ByteArray): ByteArray {
        if (!isSensitiveUniqueReference(modelId, reference)) return value
        val tokenProvider = fieldEncryptionProvider as? SensitiveIndexTokenProvider
            ?: throw RequestException("Sensitive unique property requires SensitiveIndexTokenProvider")
        return tokenProvider.deriveDeterministicToken(modelId, reference, value)
    }

    suspend fun mapUniqueValueByteCandidates(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
    ): List<ByteArray> {
        if (!isSensitiveUniqueReference(modelId, reference)) return listOf(value)
        val tokenProvider = fieldEncryptionProvider as? SensitiveIndexTokenProvider
            ?: throw RequestException("Sensitive unique property requires SensitiveIndexTokenProvider")
        return tokenProvider.deriveDeterministicTokenCandidates(modelId, reference, value)
    }

    private fun isSensitiveReference(modelId: UInt, reference: ByteArray): Boolean =
        sensitiveReferencePrefixesByModelId[modelId]?.any { prefix -> reference.hasPrefix(prefix) } == true

    private fun isSensitiveUniqueReference(modelId: UInt, reference: ByteArray): Boolean =
        sensitiveUniqueReferencesByModelId[modelId]?.any { it.contentEquals(reference) } == true

    private fun collectSensitiveReferences(modelId: UInt, dataModel: IsRootDataModel): IndexedDbSensitiveModelReferences {
        val sensitiveReferences = mutableListOf<ByteArray>()
        val sensitiveUniqueReferences = mutableListOf<ByteArray>()
        collectSensitiveReferencesRecursive(
            modelId = modelId,
            rootDataModel = dataModel,
            dataModel = dataModel,
            parentRef = null,
            sensitiveReferences = sensitiveReferences,
            sensitiveUniqueReferences = sensitiveUniqueReferences,
            modelPath = mutableListOf(),
        )
        return IndexedDbSensitiveModelReferences(sensitiveReferences, sensitiveUniqueReferences)
    }

    private fun collectSensitiveReferencesRecursive(
        modelId: UInt,
        rootDataModel: IsRootDataModel,
        dataModel: IsValuesDataModel,
        parentRef: AnyPropertyReference?,
        sensitiveReferences: MutableList<ByteArray>,
        sensitiveUniqueReferences: MutableList<ByteArray>,
        modelPath: MutableList<IsValuesDataModel>,
    ) {
        if (modelPath.any { it === dataModel }) return
        modelPath += dataModel

        try {
            dataModel.forEach { wrapper ->
                val propertyReference = wrapper.ref(parentRef)
                val reference = propertyReference.toStorageByteArray()
                if (wrapper is IsSensitiveValueDefinitionWrapper<*, *, *, *> && wrapper.sensitive) {
                    val isUnique = validateSensitiveWrapper(modelId, rootDataModel, dataModel, wrapper, propertyReference)
                    sensitiveReferences += reference
                    if (isUnique) {
                        sensitiveUniqueReferences += reference
                    }
                }

                val definition = wrapper.definition
                if (definition is EmbeddedValuesDefinition<*>) {
                    collectSensitiveReferencesRecursive(
                        modelId = modelId,
                        rootDataModel = rootDataModel,
                        dataModel = definition.dataModel,
                        parentRef = propertyReference,
                        sensitiveReferences = sensitiveReferences,
                        sensitiveUniqueReferences = sensitiveUniqueReferences,
                        modelPath = modelPath,
                    )
                }
            }
        } finally {
            modelPath.removeAt(modelPath.lastIndex)
        }
    }

    private fun validateSensitiveWrapper(
        modelId: UInt,
        rootDataModel: IsRootDataModel,
        dataModel: IsValuesDataModel,
        wrapper: IsSensitiveValueDefinitionWrapper<*, *, *, *>,
        propertyReference: AnyPropertyReference,
    ): Boolean {
        val definition = wrapper.definition
        val isUnique = (definition as? IsComparableDefinition<*, *>)?.unique == true
        val indexed = rootDataModel.Meta.indexes?.any {
            it.isForPropertyReference(propertyReference)
        } == true
        if (indexed) {
            throw RequestException(
                "Sensitive property ${dataModel.Meta.name}.${wrapper.name} (modelId=$modelId) cannot be indexed yet. Use explicit blind-index field"
            )
        }
        return isUnique
    }
}

private fun IsIndexable.isForPropertyReference(propertyReference: AnyPropertyReference): Boolean = when (this) {
    is IsIndexablePropertyReference<*> -> isForPropertyReference(propertyReference)
    is Multiple -> references.any { it is IsIndexablePropertyReference<*> && it.isForPropertyReference(propertyReference) }
    else -> false
}

private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (index in prefix.indices) {
        if (this[index] != prefix[index]) return false
    }
    return true
}
