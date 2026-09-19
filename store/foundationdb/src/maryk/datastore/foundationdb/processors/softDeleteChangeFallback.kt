package maryk.datastore.foundationdb.processors

import maryk.core.exceptions.StorageException
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
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.readVersionBytes
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
    maxVersions: UInt = UInt.MAX_VALUE,
): DataObjectVersionedChange<DM>? {
    val softDeleteStates = tr.readSoftDeleteStates(tableDirs, key, fromVersion, toVersion, maxVersions)
    if (softDeleteStates.isEmpty()) return objectChange

    val existingChanges = objectChange?.changes.orEmpty()
    val statesByVersion = existingChanges.flatMap { versioned ->
        versioned.changes.filterIsInstance<ObjectSoftDeleteChange>().map { versioned.version to it.isDeleted }
    }.toMap().toMutableMap()
    statesByVersion.putAll(softDeleteStates)
    val selectedStates = statesByVersion.entries.sortedByDescending { it.key }
        .take(maxVersions.coerceAtMost(Int.MAX_VALUE.toUInt()).toInt())
    val nonDeleteChanges = existingChanges.mapNotNull { versioned ->
        val remaining = versioned.changes.filterNot { it is ObjectSoftDeleteChange }
        if (remaining.isEmpty()) null else versioned.copy(changes = remaining)
    }
    val updatedChanges = selectedStates.fold(nonDeleteChanges) { changes, (version, isDeleted) ->
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
    maxVersions: UInt,
): List<Pair<ULong, Boolean>> {
    val qualifier = key.bytes + SOFT_DELETE_INDICATOR
    val states = mutableListOf<Pair<ULong, Boolean>>()
    if (tableDirs is HistoricTableDirectories) {
        val prefix = packKey(
            tableDirs.historicTablePrefix,
            encodeZeroFreeSuffixUsing01(qualifier, key.size),
        )
        val requestedVersion = (toVersion ?: ULong.MAX_VALUE).toReversedVersionBytes()
        val iterator = getRange(Range.startsWith(prefix)).iterator()
        while (iterator.hasNext()) {
            val entry = iterator.nextBlocking()
            val versionOffset = prefix.size + 1
            if (entry.key.size != versionOffset + VERSION_BYTE_SIZE || entry.key[prefix.size] != 0.toByte()) continue
            if (requestedVersion.compareToRange(entry.key, versionOffset) <= 0) {
                // A zero payload represents an undelete, not a historic deletion marker.
                if (entry.value.size != 1) throw StorageException("Invalid historic soft-delete marker size: ${entry.value.size}")
                val version = entry.key.readReversedVersionBytes(versionOffset)
                if (version < fromVersion) break
                states += version to (entry.value.last() == TRUE)
                if (states.size.toUInt() >= maxVersions) break
            }
        }
    }
    // Older stores may have a current marker without the matching historic row.
    // Its own timestamp must fit the requested snapshot; never project a later
    // delete or undelete into an earlier snapshot.
    val current = get(packKey(tableDirs.tablePrefix, qualifier)).awaitResult()
    if (current != null && current.size == VERSION_BYTE_SIZE + 1) {
        val version = current.readVersionBytes()
        if (version >= fromVersion && (toVersion == null || version <= toVersion) && states.none { it.first == version }) {
            states += version to (current.last() == TRUE)
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
