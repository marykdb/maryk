package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.convertStorageToValues
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.ValuesWithMetaData
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue

/**
 * Processes [record] values to a ValuesWithMeta object
 */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.recordToValueWithMeta(
    select: RootPropRefGraph<P>?,
    toVersion: ULong?,
    record: DataRecord<DM, P>
): ValuesWithMetaData<DM, P>? {
    var valueIndex = -1
    var maxVersion = record.firstVersion

    val values = this.convertStorageToValues(
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
        processValue = { _, _ ->
            val node = record.values[valueIndex]
            when (node) {
                is DataRecordValue<*> -> {
                    // Only add if  below expected version
                    if (toVersion == null || node.version < toVersion) {
                        if (node.version > maxVersion) {
                            maxVersion = node.version
                        }
                        node.value
                    } else null
                }
                is DataRecordHistoricValues<*> -> {
                    when (val latest = node.history.findLast { toVersion == null || it.version < toVersion }) {
                        null -> {} // skip because not a value
                        is DataRecordValue<*> -> {
                            if (latest.version > maxVersion) {
                                maxVersion = latest.version
                            }
                            latest.value
                        }
                        is DeletedValue<*> -> {} // skip deleted
                        else -> throw Exception("Unknown value type")
                    }
                }
                is DeletedValue<*> -> {} // Skip deleted
            }
        }
    )

    if(values.size == 0){
        // Return null if no ValueItems were found
        return null
    }
    return ValuesWithMetaData(
        key = record.key,
        values = values,
        isDeleted = record.isDeleted(toVersion),
        firstVersion = record.firstVersion,
        lastVersion = maxVersion
    )
}

/** Check if [node] is deleted */
private fun isDeletedNode(node: DataRecordNode) =
    node is DeletedValue<*> || (node is DataRecordHistoricValues<*> && node.history.last() is DeletedValue<*>)
