package maryk.datastore.memory.processors

import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.readStorageToChanges
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue

/** Processes [record] values to a DataObjectWithChanges object */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.recordToObjectChanges(
    select: RootPropRefGraph<P>?,
    fromVersion: ULong,
    toVersion: ULong?,
    record: DataRecord<DM, P>
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
                        // skip because last is below
                    } else {
                        when (val latest = node.history.findLast { it.version >= fromVersion && (toVersion == null || it.version < toVersion) }) {
                            null -> {} // skip because not a value
                            is DataRecordValue<*> -> {
                                valueWithVersionReader(latest.version.timestamp, latest.value)
                            }
                            is DeletedValue<*> -> {
                                valueWithVersionReader(latest.version.timestamp, null)
                            }
                            else -> throw TypeException("Unknown value type")
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
        changes = changes
    )
}

/** Check if [node] is deleted */
private fun isDeletedNode(node: DataRecordNode) =
    node is DeletedValue<*> || (node is DataRecordHistoricValues<*> && node.history.last() is DeletedValue<*>)
