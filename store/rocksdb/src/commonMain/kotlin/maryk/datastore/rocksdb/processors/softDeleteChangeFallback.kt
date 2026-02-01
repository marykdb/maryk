package maryk.datastore.rocksdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.VersionedChanges
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.rocksDBNotFound

internal fun <DM : IsRootDataModel> addSoftDeleteChangeIfMissing(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<DM>,
    fromVersion: ULong,
    objectChange: DataObjectVersionedChange<DM>
): DataObjectVersionedChange<DM> {
    val hasSoftDelete = objectChange.changes.any { versioned ->
        versioned.changes.any { it is ObjectSoftDeleteChange }
    }
    if (hasSoftDelete) return objectChange

    val softDeleteQualifier = key.bytes + SOFT_DELETE_INDICATOR
    val valueLength = dbAccessor.get(columnFamilies.table, readOptions, softDeleteQualifier, recyclableByteArray)
    if (valueLength == rocksDBNotFound || valueLength <= 0) return objectChange

    val version = recyclableByteArray.readVersionBytes()
    if (version < fromVersion) return objectChange

    val isDeleted = recyclableByteArray[valueLength - 1] == TRUE
    val updatedChanges = addChangeVersion(
        changes = objectChange.changes,
        version = version,
        change = ObjectSoftDeleteChange(isDeleted)
    )
    return objectChange.copy(changes = updatedChanges)
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
