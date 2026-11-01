package maryk.datastore.indexeddb

import maryk.datastore.shared.collectSensitiveReferences

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.datastore.indexeddb.processors.toBigEndianBytes
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.datastore.shared.encryption.FieldEncryptionEnvelope
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import maryk.lib.bytes.combineToByteArray

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
        if (!isSensitiveReference(modelId, reference)) return value
        val envelope = FieldEncryptionEnvelope.fromSensitiveValue(value) ?: return value
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

    suspend fun encryptChangeLogPayload(
        modelId: UInt,
        recordKey: ByteArray,
        version: ULong,
        value: ByteArray,
    ): ByteArray {
        if (sensitiveReferencePrefixesByModelId[modelId].isNullOrEmpty()) return value
        val provider = fieldEncryptionProvider as? ContextualFieldEncryptionProvider
            ?: throw RequestException("Sensitive change-log writes require ContextualFieldEncryptionProvider")
        return combineToByteArray(
            FieldEncryptionEnvelope.Contextual.magic,
            provider.encrypt(changeLogContext(modelId, recordKey, version), value),
        )
    }

    fun hasSensitiveFields(modelId: UInt): Boolean =
        !sensitiveReferencePrefixesByModelId[modelId].isNullOrEmpty()

    fun isEncryptedChangeLogPayload(value: ByteArray): Boolean =
        FieldEncryptionEnvelope.fromSensitiveValue(value) != null

    suspend fun decryptChangeLogPayloadIfNeeded(
        modelId: UInt,
        recordKey: ByteArray,
        version: ULong,
        value: ByteArray,
    ): ByteArray {
        if (sensitiveReferencePrefixesByModelId[modelId].isNullOrEmpty()) return value
        val envelope = FieldEncryptionEnvelope.fromSensitiveValue(value) ?: return value
        val provider = fieldEncryptionProvider
            ?: throw RequestException("Encrypted change log encountered but no fieldEncryptionProvider configured")
        return when (envelope) {
            FieldEncryptionEnvelope.Legacy -> provider.decrypt(
                value,
                envelope.magic.size,
                value.size - envelope.magic.size,
            )
            FieldEncryptionEnvelope.Contextual -> {
                val contextualProvider = provider as? ContextualFieldEncryptionProvider
                    ?: throw RequestException(
                        "MKE2 change log encountered but provider does not support contextual field encryption"
                    )
                contextualProvider.decrypt(
                    changeLogContext(modelId, recordKey, version),
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

    private fun changeLogContext(modelId: UInt, recordKey: ByteArray, version: ULong) = FieldEncryptionContext(
        modelId,
        recordKey,
        combineToByteArray(ChangeLogContextReference, version.toBigEndianBytes()),
    )

}

private val ChangeLogContextReference = byteArrayOf(0) + "maryk-change-log-v1:".encodeToByteArray()

private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (index in prefix.indices) {
        if (this[index] != prefix[index]) return false
    }
    return true
}
