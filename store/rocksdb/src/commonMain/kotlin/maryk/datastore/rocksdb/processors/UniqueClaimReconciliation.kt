package maryk.datastore.rocksdb.processors

import maryk.core.extensions.bytes.invert
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.hasActiveStoredType
import maryk.datastore.rocksdb.processors.helpers.deleteUniqueIndexValue
import maryk.datastore.rocksdb.processors.helpers.requireVersionedValue
import maryk.datastore.rocksdb.processors.helpers.setUniqueIndexValue
import maryk.datastore.shared.hasStoredValuePayload
import maryk.datastore.shared.UniqueException
import maryk.lib.extensions.compare.matchesRangePart

internal data class CurrentUniqueClaim(val reference: ByteArray, val candidates: List<ByteArray>)

internal fun RocksDBDataStore.captureCurrentUniqueClaims(
    model: IsRootDataModel,
    transaction: Transaction,
    families: TableColumnFamilies,
    key: ByteArray,
    affectedReferences: List<ByteArray>?,
    preferHistoricSource: Boolean = false,
): List<CurrentUniqueClaim> = buildList {
    transaction.getIterator(defaultReadOptions, families.table).use { iterator ->
        for (scope in model.uniqueClaimScopes(affectedReferences)) {
            val prefix = key + scope
            iterator.seek(prefix)
            while (iterator.isValid()) {
                val storedKey = iterator.key()
                if (!storedKey.matchesRangePart(0, prefix)) break
                val reference = storedKey.copyOfRange(key.size, storedKey.size)
                if (reference.isNotEmpty() && reference[0] != SOFT_DELETE_INDICATOR &&
                    !(reference.size == 1 && reference[0] == LAST_VERSION_INDICATOR) && reference.isAffected(affectedReferences)) {
                    var offset = 0
                    val property = model.getPropertyReferenceByStorageBytes(reference.size, { reference[offset++] })
                    if ((property.comparablePropertyDefinition as? IsComparableDefinition<*, *>)?.unique == true &&
                        transaction.hasActiveStoredType(families, defaultReadOptions, key, property, null)) {
                        val stored = iterator.value()
                        requireVersionedValue(stored)
                        if (hasStoredValuePayload(property.comparablePropertyDefinition, stored, VERSION_BYTE_SIZE, stored.size - VERSION_BYTE_SIZE)) {
                            // Use only the current cell's exact version, never a stale
                            // historical unique owner after edits while deleted.
                            val historicSource = if (preferHistoricSource && families is HistoricTableColumnFamilies) {
                                transaction.get(families.historic.table, defaultReadOptions,
                                    key + reference + stored.copyOfRange(0, VERSION_BYTE_SIZE).invert())
                            } else null
                            val source = historicSource ?: stored
                            val sourceOffset = if (historicSource == null) VERSION_BYTE_SIZE else 0
                            val candidates = withDecryptedValueIfNeeded(families.modelId, key, reference, source,
                                sourceOffset, source.size - sourceOffset) { bytes, start, length ->
                                mapUniqueValueByteCandidates(families.modelId, reference, bytes, start, length)
                            }
                            add(CurrentUniqueClaim(reference, candidates))
                        }
                    }
                }
                iterator.next()
            }
        }
    }
}

internal fun RocksDBDataStore.reconcileCurrentUniqueClaims(
    model: IsRootDataModel,
    transaction: Transaction,
    families: TableColumnFamilies,
    key: ByteArray,
    version: ByteArray,
    before: List<CurrentUniqueClaim>,
    isDeleted: Boolean,
    affectedReferences: List<ByteArray>?,
) {
    val after = if (isDeleted) emptyList() else captureCurrentUniqueClaims(model, transaction, families, key,
        affectedReferences, preferHistoricSource = affectedReferences == null)
    val retained = after.flatMap { claim -> claim.candidates.map { claim.reference + it } }
    fun owner(reference: ByteArray) = transaction.getForUpdate(defaultReadOptions, families.unique, reference)
        ?.takeIf { it.size == VERSION_BYTE_SIZE + key.size }?.copyOfRange(VERSION_BYTE_SIZE, VERSION_BYTE_SIZE + key.size)
    for (claim in before) for (value in claim.candidates) {
        val reference = claim.reference + value
        if (retained.none { it.contentEquals(reference) } && owner(reference)?.contentEquals(key) == true) {
            deleteUniqueIndexValue(transaction, families, claim.reference, value, 0, value.size, version, historicValue = key)
        }
    }
    for (claim in after) {
        for (value in claim.candidates) {
            val existing = owner(claim.reference + value)
            if (existing != null && !existing.contentEquals(key)) throw UniqueException(claim.reference, Key<IsRootDataModel>(existing))
        }
        createUniqueIndexIfNotExists(families.modelId, families.unique, claim.reference, transaction)
        val active = claim.reference + claim.candidates.first()
        if (owner(active) == null) setUniqueIndexValue(families, transaction, active, version, Key<IsRootDataModel>(key))
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
