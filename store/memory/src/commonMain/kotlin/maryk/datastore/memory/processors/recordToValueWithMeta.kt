package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.values
import maryk.core.processors.datastore.readStorageToValues
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.EmptyValueItems
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue

/**
 * Processes [record] values to a ValuesWithMeta object
 */
internal fun <DM : IsRootDataModel<P>, P : IsValuesPropertyDefinitions> DM.recordToValueWithMeta(
    select: RootPropRefGraph<P>?,
    toVersion: HLC?,
    record: DataRecord<DM, P>
): ValuesWithMetaData<DM, P>? {
    var valueIndex = -1
    var maxVersion = record.firstVersion

    val values = if (select != null && select.properties.isEmpty()) {
        // Don't read the values if no values are selected
        this.values(null) { EmptyValueItems }
    } else {
        this.readStorageToValues(
            getQualifier = { resultHandler ->
                val qualifier = record.values.getOrNull(++valueIndex)?.reference
                qualifier?.let { q ->
                    resultHandler({ q[it] }, q.size); true
                } ?: false
            },
            select = select,
            processValue = { _, _ ->
                when (val node = record.values[valueIndex]) {
                    is DataRecordValue<*> -> {
                        // Only add if below expected version
                        if (toVersion == null || node.version <= toVersion) {
                            if (node.version > maxVersion) {
                                maxVersion = node.version
                            }
                            node.value
                        } else null // Signal that at this moment the value does not exist
                    }
                    is DataRecordHistoricValues<*> -> {
                        when (val latest = node.history.findLast { toVersion == null || it.version <= toVersion }) {
                            null -> null // skip because not a value
                            is DataRecordValue<*> -> {
                                if (latest.version > maxVersion) {
                                    maxVersion = latest.version
                                }
                                latest.value
                            }
                            is DeletedValue<*> -> null
                            else -> throw TypeException("Unknown value type")
                        }
                    }
                    is DeletedValue<*> -> null
                }
            }
        )
    }

    // Return null if no values where found but values where selected
    if (values.size == 0 && (select == null || select.properties.isNotEmpty())) {
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
