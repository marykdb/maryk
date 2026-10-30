package maryk.datastore.foundationdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.hasActiveStoredType
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.packVersionedKey
import maryk.datastore.foundationdb.processors.helpers.requireVersionedValue
import maryk.datastore.foundationdb.processors.helpers.setUniqueIndexValue
import maryk.datastore.foundationdb.processors.helpers.writeHistoricUnique
import maryk.datastore.shared.hasStoredValuePayload
import maryk.datastore.shared.UniqueException
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction

internal data class CurrentUniqueClaim(val reference: ByteArray, val candidates: List<ByteArray>)

internal fun FoundationDBDataStore.captureCurrentUniqueClaims(
    model: IsRootDataModel,
    transaction: Transaction,
    directories: IsTableDirectories,
    key: ByteArray,
    affectedReferences: List<ByteArray>?,
    preferHistoricSource: Boolean = false,
): List<CurrentUniqueClaim> = buildList {
    val prefix = packKey(directories.tablePrefix, key)
    for (scope in model.uniqueClaimScopes(affectedReferences)) {
        val iterator = transaction.getRange(Range.startsWith(prefix + scope)).iterator()
        while (iterator.hasNext()) {
            val stored = iterator.nextBlocking()
            val reference = stored.key.copyOfRange(prefix.size, stored.key.size)
            if (reference.isEmpty() || reference[0] == SOFT_DELETE_INDICATOR || !reference.isAffected(affectedReferences)) continue
            var offset = 0
            val property = model.getPropertyReferenceByStorageBytes(reference.size, { reference[offset++] })
            if ((property.comparablePropertyDefinition as? IsComparableDefinition<*, *>)?.unique != true) continue
            if (!transaction.hasActiveStoredType(directories, key, property, null) { modelId, value, start, length, recordKey, qualifier ->
                    decryptValueIfNeeded(modelId, recordKey, qualifier, value, start, length)
                }) continue
            requireVersionedValue(stored.value)
            if (!hasStoredValuePayload(property.comparablePropertyDefinition, stored.value, VERSION_BYTE_SIZE, stored.value.size - VERSION_BYTE_SIZE)) continue
            // The exact source version preserves existing recovery of a damaged current
            // cell without restoring an older value changed while the record was deleted.
            val historicSource = if (preferHistoricSource && directories is HistoricTableDirectories) {
                transaction.get(packVersionedKey(directories.historicTablePrefix, key,
                    encodeZeroFreeUsing01(reference), version = stored.value.copyOfRange(0, VERSION_BYTE_SIZE))).awaitResult()
            } else null
            val source = historicSource ?: stored.value
            val sourceOffset = if (historicSource == null) VERSION_BYTE_SIZE else 0
            val candidates = withDecryptedValueIfNeeded(directories.modelId, key, reference, source,
                sourceOffset, source.size - sourceOffset) { bytes, start, length ->
                mapUniqueValueByteCandidates(directories.modelId, reference, bytes, start, length)
            }
            add(CurrentUniqueClaim(reference, candidates))
        }
    }
}

internal fun FoundationDBDataStore.reconcileCurrentUniqueClaims(
    model: IsRootDataModel,
    transaction: Transaction,
    directories: IsTableDirectories,
    key: ByteArray,
    version: ByteArray,
    before: List<CurrentUniqueClaim>,
    isDeleted: Boolean,
    affectedReferences: List<ByteArray>?,
) {
    val after = if (isDeleted) emptyList() else captureCurrentUniqueClaims(model, transaction, directories, key,
        affectedReferences, preferHistoricSource = affectedReferences == null)
    val retained = after.flatMap { claim -> claim.candidates.map { claim.reference + it } }
    fun owner(reference: ByteArray) = transaction.get(packKey(directories.uniquePrefix, reference)).awaitResult()
        ?.takeIf { it.size == VERSION_BYTE_SIZE + key.size }?.copyOfRange(VERSION_BYTE_SIZE, VERSION_BYTE_SIZE + key.size)
    for (claim in before) for (value in claim.candidates) {
        val reference = claim.reference + value
        if (retained.none { it.contentEquals(reference) } && owner(reference)?.contentEquals(key) == true) {
            transaction.clear(packKey(directories.uniquePrefix, reference))
            writeHistoricUnique(transaction, directories, key, reference, version)
        }
    }
    for (claim in after) {
        for (value in claim.candidates) {
            val existing = owner(claim.reference + value)
            if (existing != null && !existing.contentEquals(key)) throw UniqueException(claim.reference, Key<IsRootDataModel>(existing))
        }
        createUniqueIndexIfNotExists(directories.modelId, directories.uniquePrefix, claim.reference, transaction)
        val active = claim.reference + claim.candidates.first()
        if (owner(active) == null) setUniqueIndexValue(transaction, directories, active, version, key)
    }
}

private fun ByteArray.isAffected(references: List<ByteArray>?) = references == null || references.any { other ->
    (0 until minOf(size, other.size)).all { this[it] == other[it] }
}

private fun IsRootDataModel.uniqueClaimScopes(references: List<ByteArray>?): List<ByteArray> {
    if (references == null) return listOf(byteArrayOf())
    val scopes = references.toMutableList()
    for (reference in references) {
        var offset = 0
        var property: AnyPropertyReference? = getPropertyReferenceByStorageBytes(reference.size, { reference[offset++] })
        while (property != null) {
            if ((property.comparablePropertyDefinition as? IsComparableDefinition<*, *>)?.unique == true) {
                scopes += property.toStorageByteArray()
            }
            property = (property as? IsPropertyReferenceWithParent<*, *, *, *>)?.parentReference
        }
    }
    return scopes.sortedBy { it.size }.fold(mutableListOf<ByteArray>()) { selected, scope ->
        if (selected.none { existing -> existing.indices.all { scope[it] == existing[it] } }) selected += scope
        selected
    }
}
