package maryk.datastore.shared.migration

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.hasFloatingPointStorage
import maryk.core.models.migration.convertLegacyFloatingPointBytes
import maryk.core.models.migration.convertLegacyStorageQualifier
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.datastore.shared.encryption.FieldEncryptionEnvelope
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider

internal val StorageFormatTokenPrefix = "maryk.storage/token/".encodeToByteArray()

/** Shared conversion of current/historic cells and unique identities, without replaying user writes. */
class FloatingPointStorageMigration(
    private val modelId: UInt,
    private val model: IsRootDataModel,
    private val store: StorageFormatUpgradeStore,
    private val encryptionProvider: FieldEncryptionProvider?,
) {
    /** Returns the new qualifier and payload; version/record prefixes belong to the backend. */
    suspend fun convertCell(recordKey: ByteArray, qualifier: ByteArray, value: ByteArray): StorageFormatRow {
        if (qualifier.isEmpty()) return StorageFormatRow(qualifier, value) // record marker
        val resolved = model.convertLegacyStorageQualifier(qualifier)
        // Unchanged fields need neither decryption nor token regeneration. In particular,
        // opening with other models must not require keys for retained sensitive strings.
        if (resolved.bytes.contentEquals(qualifier) && resolved.definition?.hasFloatingPointStorage() != true) {
            return StorageFormatRow(qualifier, value)
        }
        val envelope = if (resolved.sensitive) FieldEncryptionEnvelope.fromSensitiveValue(value) else null
        val provider = if (envelope != null) encryptionProvider as? ContextualFieldEncryptionProvider
            ?: throw MigrationException("Storage conversion requires the contextual encryption provider for model $modelId") else null
        if (envelope == FieldEncryptionEnvelope.Legacy) throw MigrationException("Legacy field encryption requires a separate conversion before storage upgrade")
        val plain = if (envelope == null) value else provider!!.decrypt(
            FieldEncryptionContext(modelId, recordKey, qualifier), value, envelope.magic.size, value.size - envelope.magic.size,
        )
        val converted = if (resolved.valueCell) convertPayload(resolved.definition, plain) else plain
        val changed = !resolved.bytes.contentEquals(qualifier) || !converted.contentEquals(plain)
        if (resolved.sensitive && (resolved.definition as? IsComparableDefinition<*, *>)?.unique == true &&
            plain.firstOrNull() == TypeIndicator.NoTypeIndicator.byte) {
            // A one-way unique token cannot be decoded. Persist its translation from the
            // original table/history value before any unique namespaces are scanned.
            val tokens = encryptionProvider as? SensitiveIndexTokenProvider
                ?: throw MigrationException("Sensitive unique conversion requires its token provider")
            val oldTokens = tokens.deriveDeterministicTokenCandidates(modelId, qualifier, plain)
            val newTokens = tokens.deriveDeterministicTokenCandidates(modelId, resolved.bytes, converted)
            require(oldTokens.size == newTokens.size) { "Token provider changed candidates during storage conversion" }
            store.commit(oldTokens.indices.map { index ->
                StorageFormatWrite(0, tokenKey(qualifier + oldTokens[index]), resolved.bytes + newTokens[index])
            })
        }
        val target = if (envelope != null && changed) FieldEncryptionEnvelope.Contextual.magic + provider!!.encrypt(
            FieldEncryptionContext(modelId, recordKey, resolved.bytes), converted,
        ) else if (envelope != null) value else converted
        return StorageFormatRow(resolved.bytes, target)
    }

    /** A unique reference marker has no payload and remains a marker after conversion. */
    suspend fun convertUnique(bytes: ByteArray): ByteArray {
        val resolved = model.convertLegacyStorageQualifier(bytes, unique = true)
        if (resolved.length == bytes.size) return resolved.bytes
        if (resolved.sensitive && resolved.bytes.copyOfRange(0, resolved.length).contentEquals(bytes.copyOfRange(0, resolved.length)) && resolved.definition?.hasFloatingPointStorage() != true) return bytes
        if (resolved.sensitive) return store.get(0, tokenKey(bytes))
            ?: throw MigrationException("Missing source value for sensitive unique conversion in model $modelId")
        return resolved.bytes.copyOfRange(0, resolved.length) +
            convertPayload(resolved.definition, bytes.copyOfRange(resolved.length, bytes.size))
    }

    private suspend fun tokenKey(bytes: ByteArray): ByteArray {
        val id = ByteArray(4)
        var offset = 0
        modelId.writeBytes({ id[offset++] = it })
        return StorageFormatTokenPrefix + CryptographyProvider.Default.get(SHA256).hasher().hash(id + bytes)
    }
}

/** Count/deletion/embed markers are not numeric property values. */
private fun convertPayload(definition: IsPropertyDefinition<*>?, bytes: ByteArray): ByteArray {
    if (definition == null || bytes.isEmpty()) return bytes
    var offset = 0
    return initUIntByVarWithExtraInfo({ bytes[offset++] }) { type, indicator ->
        val valueDefinition = when (indicator) {
            TypeIndicator.NoTypeIndicator.byte -> definition
            TypeIndicator.SimpleTypeIndicator.byte -> ((definition as? IsMultiTypeDefinition<*, *, *>)?.typeEnum?.resolve(type) as? MultiTypeEnum<*>)?.definition
                ?: throw MigrationException("Missing legacy value type $type")
            else -> return@initUIntByVarWithExtraInfo bytes
        }
        bytes.copyOfRange(0, offset) + valueDefinition.convertLegacyFloatingPointBytes(bytes.copyOfRange(offset, bytes.size))
    }
}
