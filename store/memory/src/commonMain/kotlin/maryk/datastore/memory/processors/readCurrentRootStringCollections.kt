package maryk.datastore.memory.processors

import maryk.core.models.IsRootDataModel
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.emptyValues
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.ReferenceType.LIST
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.properties.references.ReferenceType.DELETE
import maryk.core.properties.references.ReferenceType.VALUE
import maryk.core.properties.types.TypedValue
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataRecordHistoricValues
import maryk.datastore.memory.records.DataRecordNode
import maryk.datastore.memory.records.DataRecordValue
import maryk.datastore.memory.records.DeletedValue
import maryk.datastore.memory.records.IsDataRecordValue
import maryk.lib.bytes.initString
import maryk.lib.exceptions.ParseException

/** Current full reads of proven root string collections need no transient item references. */
internal fun <DM : IsRootDataModel> DM.readCurrentRootStringCollections(record: DataRecord<DM>): ValuesWithMetaData<DM>? {
    var hasCollection = false
    for (property in this) {
        if (property.index !in 1u..15u) return null
        when (val definition = property.definition) {
            is IsSimpleValueDefinition<*, *> -> Unit
            is IsListDefinition<*, *> -> {
                if (definition.valueDefinition !is StringDefinition) return null
                hasCollection = true
            }
            is IsMapDefinition<*, *, *> -> {
                if (definition.keyDefinition !is StringDefinition || definition.valueDefinition !is StringDefinition) return null
                hasCollection = true
            }
            else -> return null
        }
    }
    if (!hasCollection) return null

    val items = ArrayList<ValueItem>(size)
    var maxVersion = record.lastVersion
    var position = 0
    var previousIndex = 0
    while (position < record.values.size) {
        val node = record.values[position++]
        val qualifier = node.reference
        if (qualifier.size != 1) return null
        val header = qualifier[0].toInt()
        if (header < 0) return null
        val current = node.currentNode() ?: return null
        if (current.version > maxVersion) maxVersion = current.version
        if (header == DELETE.value.toInt()) {
            when (current) {
                is DataRecordValue<*> -> if (current.value !is Boolean) return null
                is DeletedValue<*> -> Unit
                else -> return null
            }
            continue
        }
        val index = header ushr 3
        if (index <= previousIndex) return null
        previousIndex = index
        val definition = this[index.toUInt()]?.definition ?: return null
        when (header and 7) {
            VALUE.value.toInt() -> {
                if (definition !is IsSimpleValueDefinition<*, *>) return null
                when (val latest = current) {
                    is DataRecordValue<*> -> {
                        if (latest.value is TypeEnum<*> || latest.value is TypedValue<*, *>) return null
                        items += ValueItem(index.toUInt(), latest.value)
                    }
                    is DeletedValue<*> -> Unit
                    else -> return null
                }
            }
            LIST.value.toInt() -> {
                if (definition !is IsListDefinition<*, *> || definition.valueDefinition !is StringDefinition) return null
                val count = (current as? DataRecordValue<*>)?.value as? Int ?: return null
                if (count < 0 || count > record.values.size - position) return null
                val list = ArrayList<String>(count)
                while (position < record.values.size) {
                    val member = record.values[position]
                    val bytes = member.reference
                    if (bytes.isEmpty() || bytes[0] != qualifier[0]) break
                    if (bytes.size != 5) return null
                    val itemIndex = ((bytes[1].toInt() and 255) shl 24) or
                        ((bytes[2].toInt() and 255) shl 16) or
                        ((bytes[3].toInt() and 255) shl 8) or (bytes[4].toInt() and 255)
                    val latest = member.currentNode() ?: return null
                    if (latest.version > maxVersion) maxVersion = latest.version
                    when (latest) {
                        is DataRecordValue<*> -> {
                            val value = latest.value as? String ?: return null
                            if (itemIndex != list.size || list.size >= count) return null
                            list += value
                        }
                        is DeletedValue<*> -> Unit
                        else -> return null
                    }
                    position++
                }
                if (list.size != count) return null
                items += ValueItem(index.toUInt(), list)
            }
            MAP.value.toInt() -> {
                if (definition !is IsMapDefinition<*, *, *> || definition.keyDefinition !is StringDefinition ||
                    definition.valueDefinition !is StringDefinition) return null
                val count = (current as? DataRecordValue<*>)?.value as? Int ?: return null
                if (count < 0 || count > record.values.size - position) return null
                val map = LinkedHashMap<String, String>(count)
                while (position < record.values.size) {
                    val member = record.values[position]
                    val bytes = member.reference
                    if (bytes.isEmpty() || bytes[0] != qualifier[0]) break
                    val key = readStringMapKey(bytes) ?: return null
                    val latest = member.currentNode() ?: return null
                    if (latest.version > maxVersion) maxVersion = latest.version
                    when (latest) {
                        is DataRecordValue<*> -> {
                            val value = latest.value as? String ?: return null
                            if (map.put(key, value) != null || map.size > count) return null
                        }
                        is DeletedValue<*> -> Unit
                        else -> return null
                    }
                    position++
                }
                if (map.size != count) return null
                items += ValueItem(index.toUInt(), map)
            }
            else -> return null
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

private fun readStringMapKey(bytes: ByteArray): String? {
    var offset = 1
    val length = try {
        initIntByVar { bytes[offset++] }
    } catch (_: ParseException) {
        return null
    }
    if (length < 0 || length != bytes.size - offset) return null
    return initString(length) { bytes[offset++] }
}

private fun DataRecordNode.currentNode(): IsDataRecordValue<*>? = when (this) {
    is DataRecordHistoricValues<*> -> history.lastOrNull()
    is DataRecordValue<*> -> this
    is DeletedValue<*> -> this
}
