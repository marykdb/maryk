package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.convertStorageToValues
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.ValuesWithMetaData
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue

/**
 * Processes [record] values to a ValuesWithMeta object
 */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.recordToValueWithMeta(
    select: RootPropRefGraph<P>?,
    toVersion: HLC?,
    record: DataRecord<DM, P>
): ValuesWithMetaData<DM, P>? {
    var valueIndex = -1
    var maxVersion = record.firstVersion

    val values = this.convertStorageToValues(
        getQualifier = { resultHandler ->
            val qualifier = record.values.getOrNull(++valueIndex)?.reference
            qualifier?.let { q ->
                resultHandler({ q[it] }, q.size); true
            } ?: false
        },
        select = select,
        processValue = { _, _, valueWithVersionReader ->
            when (val node = record.values[valueIndex]) {
                is DataRecordValue<*> -> {
                    // Only add if below expected version
                    if (toVersion == null || node.version <= toVersion) {
                        if (node.version > maxVersion) {
                            maxVersion = node.version
                        }
                        valueWithVersionReader(node.version.timestamp, node.value)
                    } else {
                        valueWithVersionReader(node.version.timestamp, null)
                    }
                }
                is DataRecordHistoricValues<*> -> {
                    when (val latest = node.history.findLast { toVersion == null || it.version <= toVersion }) {
                        null -> {} // skip because not a value
                        is DataRecordValue<*> -> {
                            if (latest.version > maxVersion) {
                                maxVersion = latest.version
                            }
                            valueWithVersionReader(latest.version.timestamp, latest.value)
                        }
                        is DeletedValue<*> -> valueWithVersionReader(latest.version.timestamp, null)
                        else -> throw TypeException("Unknown value type")
                    }
                }
                is DeletedValue<*> -> {
                    valueWithVersionReader(node.version.timestamp, null)
                }
            }
        }
    )

    if (values.size == 0) {
        // Return null if no ValueItems were found
        return null
    }
    return ValuesWithMetaData(
        key = record.key,
        values = values,
        isDeleted = record.isDeleted(toVersion),
        firstVersion = record.firstVersion.timestamp,
        lastVersion = maxVersion.timestamp
    )
}
