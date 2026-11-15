package maryk.datastore.memory.processors

import maryk.core.exceptions.TypeException
import maryk.core.clock.HLC
import maryk.core.processors.datastore.readStorageToChanges
import maryk.core.models.IsRootDataModel
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue
import maryk.datastore.memory.records.lastVersionIndex

/** Processes [record] values to a DataObjectWithChanges object */
internal fun <DM : IsRootDataModel> DM.recordToObjectChanges(
    select: RootPropRefGraph<DM>?,
    fromVersion: ULong,
    toVersion: ULong?,
    maxVersions: UInt,
    sortingKey: ByteArray?,
    record: DataRecord<DM>,
    keepAllVersions: Boolean,
    projectedQualifiers: List<ByteArray>? = null,
): DataObjectVersionedChange<DM>? {
    // Replication can leave the header behind its cells; prove both are older
    // before skipping qualifier decoding for a caught-up reader.
    if (record.firstVersion.timestamp < fromVersion && record.lastVersion.timestamp < fromVersion && record.values.none { node ->
            val latest = when (node) {
                is DataRecordHistoricValues<*> -> node.history.lastOrNull()
                is DataRecordValue<*> -> node
                is DeletedValue<*> -> node
            }
            latest != null && latest.version.timestamp >= fromVersion
        }
    ) return null

    val creationVersion = record.firstVersion.timestamp
    if (maxVersions > 0u && creationVersion >= fromVersion && (toVersion == null || creationVersion <= toVersion) &&
        record.lastVersion == record.firstVersion && record.values.all { node ->
            val value = when (node) {
                is DataRecordValue<*> -> node
                is DataRecordHistoricValues<*> -> node.history.singleOrNull() as? DataRecordValue<*>
                else -> null
            }
            value != null && value.version == record.firstVersion && !node.reference.contentEquals(objectSoftDeleteQualifier)
        }
    ) {
        recordToValueWithMeta(select, null, record, projectedQualifiers)?.let { snapshot ->
            return DataObjectVersionedChange(record.key, sortingKey?.let(::Bytes), listOf(
                VersionedChanges(creationVersion, buildList {
                    add(ObjectCreate)
                    if (snapshot.values.size > 0) addAll(snapshot.values.toChanges())
                }),
            ))
        }
    }
    var valueIndex = -1

    val changes = this.readStorageToChanges(
        getQualifier = { resultHandler ->
            valueIndex++

            if (valueIndex < record.values.size) {
                val qualifier = record.values[valueIndex].reference
                resultHandler({ qualifier[it] }, qualifier.size)
                true
            } else false
        },
        select = select,
        creationVersion = if(record.firstVersion.timestamp >= fromVersion) record.firstVersion.timestamp else null,
        readSelectedValuesAtVersion = { version, snapshotSelect ->
            val readVersion = if (keepAllVersions) HLC(version) else null
            recordToValueWithMeta(snapshotSelect, readVersion, record)?.values
        },
        processValue = { _, _, valueWithVersionReader ->
            when (val node = record.values[valueIndex]) {
                is DataRecordValue<*> -> {
                    // Only add if within the requested version bound
                    if (node.version >= fromVersion && (toVersion == null || node.version <= toVersion)) {
                        valueWithVersionReader(node.version.timestamp, node.value)
                    }
                }
                is DataRecordHistoricValues<*> -> {
                    if (node.history.last().version < fromVersion) {
                        // skip value because last is below fromVersion
                    } else {
                        val lastIndex = if (toVersion == null) {
                            node.history.lastIndex
                        } else {
                            node.history.lastVersionIndex(HLC(toVersion)) { it.version }
                        }

                        if(lastIndex != -1) {
                            val maxCount = minOf(maxVersions, (lastIndex + 1).toUInt()).toInt()
                            for (count in 0 until maxCount) {
                                val currentValue = node.history.getOrNull(lastIndex - count)
                                    ?: break // No values left so break the loop

                                if (currentValue.version < fromVersion) {
                                    break // Before from version so break
                                }

                                when (currentValue) {
                                    is DataRecordValue<*> ->
                                        valueWithVersionReader(currentValue.version.timestamp, currentValue.value)
                                    is DeletedValue<*> ->
                                        valueWithVersionReader(currentValue.version.timestamp, null)
                                    else -> throw TypeException("Unknown value type")
                                }
                            }
                        }
                    }
                }
                is DeletedValue<*> -> {
                    if (node.version >= fromVersion && (toVersion == null || node.version <= toVersion)) {
                        valueWithVersionReader(node.version.timestamp, null)
                    }
                }
            }
        }
    )

    if (changes.isEmpty()) {
        // Return null if no ValueItems were found
        return null
    }
    return DataObjectVersionedChange(
        key = record.key,
        sortingKey = sortingKey?.let(::Bytes),
        changes = changes
    )
}
