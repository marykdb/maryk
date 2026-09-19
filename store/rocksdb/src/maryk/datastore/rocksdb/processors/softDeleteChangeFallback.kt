package maryk.datastore.rocksdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.VersionedChanges
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
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
    objectChange: DataObjectVersionedChange<DM>?,
    sortingKey: ByteArray? = null
): DataObjectVersionedChange<DM>? {
    val hasSoftDelete = objectChange?.changes?.any { versioned ->
        versioned.changes.any { it is ObjectSoftDeleteChange }
    } == true
    if (hasSoftDelete) return objectChange

    val softDeleteQualifier = key.bytes + SOFT_DELETE_INDICATOR
    val valueLength = dbAccessor.get(columnFamilies.table, readOptions, softDeleteQualifier, recyclableByteArray)
    if (valueLength == rocksDBNotFound || valueLength != VERSION_BYTE_SIZE + 1) return objectChange

    val value = if (valueLength > recyclableByteArray.size) {
        dbAccessor.get(columnFamilies.table, readOptions, softDeleteQualifier) ?: return objectChange
    } else {
        recyclableByteArray
    }
    val version = value.readVersionBytes()
    if (version < fromVersion) return objectChange

    val isDeleted = value[valueLength - 1] == TRUE
    val updatedChanges = addChangeVersion(
        changes = objectChange?.changes ?: emptyList(),
        version = version,
        change = ObjectSoftDeleteChange(isDeleted)
    )
    return DataObjectVersionedChange(
        key = key,
        sortingKey = objectChange?.sortingKey ?: sortingKey?.let(::Bytes),
        changes = updatedChanges
    )
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
