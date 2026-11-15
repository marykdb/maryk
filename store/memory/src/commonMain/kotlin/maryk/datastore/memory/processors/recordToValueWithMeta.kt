package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.lib.extensions.compare.compareTo
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.emptyValues
import maryk.core.processors.datastore.readStorageToValues
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.TypedValue
import maryk.core.values.ValueItem
import maryk.core.values.MutableValueItems
import maryk.core.query.ValuesWithMetaData
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue
import maryk.datastore.memory.records.lastVersionIndex

/**
 * Processes [record] values to a ValuesWithMeta object
 */
internal fun <DM : IsRootDataModel> DM.recordToValueWithMeta(
    select: RootPropRefGraph<DM>?,
    toVersion: HLC?,
    record: DataRecord<DM>,
    projectedQualifiers: List<ByteArray>? = null,
): ValuesWithMetaData<DM>? {
    if (toVersion != null && record.firstVersion > toVersion) return null
    if (toVersion == null && select == null && record.values.size >= 32 && size <= 16) {
        readCurrentRootStringCollections(record)?.let { return it }
    }
    if (toVersion == null && (select == null || select.properties.isNotEmpty()) && hasSimpleRootValues(record)) {
        return readSimpleRootValues(select, record)
    }
    var valueIndex = -1
    var maxVersion = if (select == null && toVersion == null) record.lastVersion else record.firstVersion

    val values = if (select != null && select.properties.isEmpty()) {
        // Don't read the values if no values are selected
        this.emptyValues()
    } else {
        this.readStorageToValues(
            getQualifier = if (projectedQualifiers == null) {
                { resultHandler ->
                    val qualifier = record.values.getOrNull(++valueIndex)?.reference
                    qualifier?.let { q ->
                        resultHandler({ q[it] }, q.size); true
                    } == true
                }
            } else {
                var targetIndex = 0
                nextProjected@{ resultHandler ->
                    while (targetIndex < projectedQualifiers.size) {
                        val target = projectedQualifiers[targetIndex++]
                        val found = record.values.binarySearch(fromIndex = valueIndex + 1) { it.reference compareTo target }
                        if (found < 0) {
                            valueIndex = -found - 2
                        } else {
                            valueIndex = found
                            val qualifier = record.values[found].reference
                            resultHandler({ qualifier[it] }, qualifier.size)
                            return@nextProjected true
                        }
                    }
                    false
                }
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
                        val historyIndex = if (toVersion == null) node.history.lastIndex
                            else node.history.lastVersionIndex(toVersion) { it.version }
                        when (val latest = node.history.getOrNull(historyIndex)) {
                            null -> null // skip because not a value
                            is DataRecordValue<*> -> {
                                if (latest.version > maxVersion) {
                                    maxVersion = latest.version
                                }
                                latest.value
                            }
                            is DeletedValue<*> -> {
                                if (latest.version > maxVersion) maxVersion = latest.version
                                null
                            }
                            else -> throw TypeException("Unknown value type")
                        }
                    }
                    is DeletedValue<*> -> {
                        if ((toVersion == null || node.version <= toVersion) && node.version > maxVersion) maxVersion = node.version
                        null
                    }
                }
            }
        )
    }

    // Record identity survives even when its last optional property has been removed.
    return ValuesWithMetaData(
        key = record.key,
        values = values,
        isDeleted = record.isDeleted(toVersion),
        firstVersion = record.firstVersion.timestamp,
        lastVersion = maxVersion.timestamp
    )
}


private fun <DM : IsRootDataModel> DM.readSimpleRootValues(
    select: RootPropRefGraph<DM>?,
    record: DataRecord<DM>,
): ValuesWithMetaData<DM> {
    var maxVersion = if (select == null) record.lastVersion else record.firstVersion
    // The validated scalar qualifiers below have root indices 1 through 15.
    var selectedRoots = 0
    if (select != null) for (property in select.properties) {
        if (property.index in 1u..15u) selectedRoots = selectedRoots or (1 shl property.index.toInt())
    }
    val items = ArrayList<ValueItem>(minOf(record.values.size, select?.properties?.size ?: record.values.size))
    for (node in record.values) {
        val index = (node.reference[0].toInt() ushr 3).toUInt()
        if (select != null && selectedRoots and (1 shl index.toInt()) == 0) continue
        val latest = if (node is DataRecordHistoricValues<*>) node.history.lastOrNull() else node
        when (latest) {
            is DataRecordValue<*> -> {
                if (latest.version > maxVersion) maxVersion = latest.version
                items += ValueItem(index, latest.value)
            }
            is DeletedValue<*> -> if (latest.version > maxVersion) maxVersion = latest.version
            null -> Unit
            else -> throw TypeException("Unknown value type")
        }
    }
    return ValuesWithMetaData(
        key = record.key,
        values = emptyValues().copy(MutableValueItems(items)),
        isDeleted = record.isDeleted(null),
        firstVersion = record.firstVersion.timestamp,
        lastVersion = maxVersion.timestamp,
    )
}

/** The general reader owns nested, typed, deleted-object and custom-reference shapes. */
private fun IsRootDataModel.hasSimpleRootValues(record: DataRecord<*>): Boolean {
    var previousIndex = 0
    for (node in record.values) {
        val qualifier = node.reference
        if (qualifier.size != 1) return false
        val byte = qualifier[0].toInt()
        if (byte < 0 || byte and 7 != 1) return false
        val index = byte ushr 3
        if (index <= previousIndex) return false
        previousIndex = index
        val wrapper = this[index.toUInt()] ?: return false
        if (wrapper !is FlexBytesDefinitionWrapper<*, *, *, *, *> &&
            wrapper !is FixedBytesDefinitionWrapper<*, *, *, *, *>) return false
        if (wrapper.definition !is IsSimpleValueDefinition<*, *>) return false
        val latest = if (node is DataRecordHistoricValues<*>) node.history.lastOrNull() else node
        if (latest is DataRecordValue<*> && (latest.value is TypeEnum<*> || latest.value is TypedValue<*, *>)) return false
    }
    return true
}
