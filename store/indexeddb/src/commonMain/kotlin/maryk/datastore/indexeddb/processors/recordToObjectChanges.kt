package maryk.datastore.indexeddb.processors

import maryk.core.exceptions.TypeException
import maryk.core.processors.datastore.readStorageToChanges
import maryk.core.models.IsRootDataModel
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.datastore.indexeddb.records.DataRecord
import maryk.datastore.indexeddb.records.DataRecordHistoricValues
import maryk.datastore.indexeddb.records.DataRecordNode
import maryk.datastore.indexeddb.records.DataRecordValue
import maryk.datastore.indexeddb.records.DeletedValue

/** Processes [record] values to a DataObjectWithChanges object */
internal fun <DM : IsRootDataModel> DM.recordToObjectChanges(
    select: RootPropRefGraph<DM>?,
    fromVersion: ULong,
    toVersion: ULong?,
    maxVersions: UInt,
    sortingKey: ByteArray?,
    record: DataRecord<DM>
): DataObjectVersionedChange<DM>? {
    var valueIndex = -1

    val changes = this.readStorageToChanges(
        getQualifier = { resultHandler ->
            valueIndex++

            // skip deleted values
            while (valueIndex < record.values.size && isDeletedNode(record.values[valueIndex])) {
                valueIndex++
            }

            if (valueIndex < record.values.size) {
                val qualifier = record.values[valueIndex].reference
                resultHandler({ qualifier[it] }, qualifier.size)
                true
            } else false
        },
        select = select,
        creationVersion = if(record.firstVersion.timestamp > fromVersion) record.firstVersion.timestamp else null,
        processValue = { _, _, valueWithVersionReader ->
            when (val node = record.values[valueIndex]) {
                is DataRecordValue<*> -> {
                    // Only add if below expected version
                    if (node.version >= fromVersion && (toVersion == null || node.version < toVersion)) {
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
                            node.history.indexOfLast { it.version < toVersion }
                        }

                        if(lastIndex != -1) {
                            for (count in 0 until maxVersions.toInt()) {
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
                    if (node.version >= fromVersion && (toVersion == null || node.version < toVersion)) {
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

/** Check if [node] is deleted */
private fun isDeletedNode(node: DataRecordNode) =
    node is DeletedValue<*> || (node is DataRecordHistoricValues<*> && node.history.last() is DeletedValue<*>)
