package maryk.datastore.foundationdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.VersionedChanges
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeSuffixUsing01
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.lib.extensions.compare.compareToRange

internal fun <DM : IsRootDataModel> addSoftDeleteChangeIfMissing(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<DM>,
    fromVersion: ULong,
    objectChange: DataObjectVersionedChange<DM>?,
    sortingKey: ByteArray? = null,
    toVersion: ULong? = null,
): DataObjectVersionedChange<DM>? {
    val hasSoftDelete = objectChange?.changes?.any { versioned ->
        versioned.changes.any { it is ObjectSoftDeleteChange }
    } == true
    if (hasSoftDelete) return objectChange

    val softDeleteStates = tr.readSoftDeleteStates(tableDirs, key, fromVersion, toVersion)
    if (softDeleteStates.isEmpty()) return objectChange

    val updatedChanges = softDeleteStates.fold(objectChange?.changes ?: emptyList()) { changes, (version, isDeleted) ->
        addChangeVersion(changes, version, ObjectSoftDeleteChange(isDeleted))
    }
    return DataObjectVersionedChange(
        key = key,
        sortingKey = objectChange?.sortingKey ?: sortingKey?.let(::Bytes),
        changes = updatedChanges
    )
}

private fun <DM : IsRootDataModel> Transaction.readSoftDeleteStates(
    tableDirs: IsTableDirectories,
    key: Key<DM>,
    fromVersion: ULong,
    toVersion: ULong?,
): List<Pair<ULong, Boolean>> {
    val qualifier = key.bytes + SOFT_DELETE_INDICATOR
    val historicDirs = tableDirs as? HistoricTableDirectories ?: return emptyList()
    val prefix = packKey(
        historicDirs.historicTablePrefix,
        encodeZeroFreeSuffixUsing01(qualifier, key.size),
    )
    val requestedVersion = (toVersion ?: ULong.MAX_VALUE).toReversedVersionBytes()
    val states = mutableListOf<Pair<ULong, Boolean>>()
    val iterator = getRange(Range.startsWith(prefix)).iterator()
    while (iterator.hasNext()) {
        val entry = iterator.nextBlocking()
        val versionOffset = prefix.size + 1
        if (entry.key.size != versionOffset + VERSION_BYTE_SIZE || entry.key[prefix.size] != 0.toByte()) continue
        if (requestedVersion.compareToRange(entry.key, versionOffset) <= 0) {
            // `false` is encoded as a zero byte, which is also the generic historic
            // delete-marker byte. Soft-delete rows use that byte as a valid state.
            if (entry.value.size != 1) return emptyList()
            val version = entry.key.readReversedVersionBytes(versionOffset)
            if (version < fromVersion) break
            states += version to (entry.value.last() == TRUE)
        }
    }
    return states
}

private fun addChangeVersion(
    changes: List<VersionedChanges>,
    version: ULong,
    change: ObjectSoftDeleteChange
): List<VersionedChanges> {
    val index = changes.indexOfFirst { it.version == version }
    if (index >= 0) {
        val existing = changes[index]
        if (existing.changes.any { it is ObjectSoftDeleteChange }) return changes
        val updated = existing.copy(changes = existing.changes + change)
        return changes.toMutableList().also { it[index] = updated }
    }

    val insertIndex = changes.binarySearch { it.version compareTo version }
    val mutable = changes.toMutableList()
    val position = if (insertIndex < 0) (insertIndex * -1) - 1 else insertIndex
    mutable.add(position, VersionedChanges(version, listOf(change)))
    return mutable
}
