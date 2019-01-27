package maryk.datastore.memory.processors

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
        getQualifier = {
            valueIndex++

            // skip deleted values
            while (valueIndex < record.values.size  && isDeletedNode(record.values[valueIndex])) {
                valueIndex++
            }

            if (valueIndex < record.values.size) {
                record.values[valueIndex].reference
            } else null
        },
        select = select,
        processValue = { _, _, valueWithVersionReader ->
            val node = record.values[valueIndex]
            when (node) {
                is DataRecordValue<*> -> {
                    // Only add if  below expected version
                    if (node.version >= fromVersion && (toVersion == null || node.version < toVersion)) {
                        valueWithVersionReader(node.version, node.value)
                    }
                }
                is DataRecordHistoricValues<*> -> {
                    if (node.history.last().version < fromVersion) {
                        // skip because last is below
                    } else {
                        when (val latest = node.history.findLast { it.version >= fromVersion && (toVersion == null || it.version < toVersion) }) {
                            null -> {} // skip because not a value
                            is DataRecordValue<*> -> {
                                valueWithVersionReader(latest.version, latest.value)
                            }
                            is DeletedValue<*> -> {
                                valueWithVersionReader(latest.version, null)
                            }
                            else -> throw Exception("Unknown value type")
                        }
                    }
                }
                is DeletedValue<*> -> {
                    if (node.version >= fromVersion && (toVersion == null || node.version < toVersion)) {
                        valueWithVersionReader(node.version, null)
                    }
                }
            }
        }
    )

    if(changes.isEmpty()){
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
