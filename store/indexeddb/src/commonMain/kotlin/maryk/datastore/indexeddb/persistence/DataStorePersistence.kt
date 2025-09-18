package maryk.datastore.indexeddb.persistence

import maryk.core.base64.Base64Maryk
import maryk.core.clock.HLC
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.models.emptyValues
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.Values
import maryk.datastore.indexeddb.records.DataRecord
import maryk.datastore.indexeddb.records.DataRecordHistoricValues
import maryk.datastore.indexeddb.records.DataRecordNode
import maryk.datastore.indexeddb.records.DataRecordValue
import maryk.datastore.indexeddb.records.DataStore
import maryk.datastore.indexeddb.records.DeletedValue
import maryk.datastore.indexeddb.processors.recordToValueWithMeta
import maryk.json.JsonReader
import maryk.json.JsonToken
import maryk.json.JsonWriter

internal fun <DM : IsRootDataModel> DataStore<DM>.toPersistedDataStore(
    dataModel: DM
): PersistedDataStore {
    val persistedRecords = this.records.map { record ->
        record.toPersistedRecord(dataModel)
    }

    return PersistedDataStore(persistedRecords)
}

internal fun <DM : IsRootDataModel> PersistedDataStore.toDataStore(
    dataModel: DM,
    keepAllVersions: Boolean
): DataStore<DM> {
    val dataStore = DataStore<DM>(keepAllVersions)

    for (persistedRecord in this.records) {
        val record = persistedRecord.toDataRecord(dataModel)
        dataStore.records += record
        rebuildIndexesForRecord(dataStore, dataModel, record)
    }

    return dataStore
}

private fun <DM : IsRootDataModel> DataRecord<DM>.toPersistedRecord(
    dataModel: DM
): PersistedRecord {
    this.values.forEach(DataRecordNode::commit)
    val persistedNodes = this.values.map { node ->
        node.toPersistedNode(dataModel)
    }

    return PersistedRecord(
        key = this.key.bytes.toBase64String(),
        firstVersion = this.firstVersion.timestamp.toString(),
        lastVersion = this.lastVersion.timestamp.toString(),
        values = persistedNodes
    )
}

private fun <DM : IsRootDataModel> DataRecordNode.toPersistedNode(
    dataModel: DM
): PersistedNode = when (this) {
    is DataRecordValue<*> -> PersistedValueNode(
        reference = this.reference.toBase64String(),
        version = this.version.timestamp.toString(),
        valueJson = dataModel.valueToJson(this.reference, this.value),
        isDeleted = false
    )
    is DeletedValue<*> -> PersistedValueNode(
        reference = this.reference.toBase64String(),
        version = this.version.timestamp.toString(),
        valueJson = null,
        isDeleted = true
    )
    is DataRecordHistoricValues<*> -> PersistedHistoricNode(
        reference = this.reference.toBase64String(),
        history = this.history.map { historic ->
            when (historic) {
                is DataRecordValue<*> -> PersistedValueNode(
                    reference = this.reference.toBase64String(),
                    version = historic.version.timestamp.toString(),
                    valueJson = dataModel.valueToJson(this.reference, historic.value),
                    isDeleted = false
                )
                is DeletedValue<*> -> PersistedValueNode(
                    reference = this.reference.toBase64String(),
                    version = historic.version.timestamp.toString(),
                    valueJson = null,
                    isDeleted = true
                )
                else -> throw IllegalStateException("Unsupported historic value ${historic::class}")
            }
        }
    )
}

private fun <DM : IsRootDataModel> PersistedRecord.toDataRecord(
    dataModel: DM
): DataRecord<DM> {
    val keyBytes = this.key.fromBase64String()
    val nodes = this.values.map { node ->
        when (node) {
            is PersistedValueNode -> node.toDataRecordNode(dataModel)
            is PersistedHistoricNode -> node.toHistoricDataRecordNode(dataModel)
        }
    }.toMutableList()

    return DataRecord(
        key = dataModel.key(keyBytes),
        values = nodes,
        firstVersion = HLC(this.firstVersion.toULong()),
        lastVersion = HLC(this.lastVersion.toULong())
    )
}

private fun <DM : IsRootDataModel> PersistedValueNode.toDataRecordNode(
    dataModel: DM
): DataRecordNode {
    val referenceBytes = this.reference.fromBase64String()
    val version = HLC(this.version.toULong())

    return if (this.isDeleted) {
        DeletedValue<Any>(referenceBytes, version)
    } else {
        val value = this.valueJson?.let { json ->
            dataModel.valueFromJson(referenceBytes, json)
        } ?: throw IllegalStateException("Value JSON expected for non-deleted node")

        DataRecordValue(referenceBytes, value, version)
    }
}

private fun <DM : IsRootDataModel> PersistedHistoricNode.toHistoricDataRecordNode(
    dataModel: DM
): DataRecordNode {
    val referenceBytes = this.reference.fromBase64String()
    val history = this.history.map { entry ->
        val version = HLC(entry.version.toULong())
        if (entry.isDeleted) {
            DeletedValue<Any>(referenceBytes, version)
        } else {
            val value = entry.valueJson?.let { json ->
                dataModel.valueFromJson(referenceBytes, json)
            } ?: throw IllegalStateException("Value JSON expected for non-deleted historic node")

            DataRecordValue(referenceBytes, value, version)
        }
    }.toMutableList()

    return DataRecordHistoricValues(referenceBytes, history)
}

private fun <DM : IsRootDataModel> rebuildIndexesForRecord(
    dataStore: DataStore<DM>,
    dataModel: DM,
    record: DataRecord<DM>
) {
    val valuesWithMeta = dataModel.recordToValueWithMeta(null, null, record)
        ?: ValuesWithMetaData(
            key = record.key,
            values = dataModel.emptyValues(),
            isDeleted = false,
            firstVersion = record.firstVersion.timestamp,
            lastVersion = record.lastVersion.timestamp
        )

    val values = valuesWithMeta.values
    val version = record.lastVersion

    dataModel.Meta.indexes?.forEach { indexDefinition ->
        val indexBytes = indexDefinition.toStorageByteArrayForIndex(values, record.key.bytes)
            ?: return@forEach
        dataStore.addToIndex(
            record = record,
            indexName = indexDefinition.referenceStorageByteArray.bytes,
            value = indexBytes,
            version = version
        )
    }

    values.writeUniqueValues { reference, comparableValue ->
        dataStore.addToUniqueIndex(
            record = record,
            indexName = reference,
            value = comparableValue,
            version = version
        )
    }
}

private fun <DM : IsRootDataModel> Values<DM>.writeUniqueValues(
    handleUnique: (ByteArray, Comparable<Any>) -> Unit
) {
    this.writeToStorage { _, reference, definition, value ->
        val comparableDefinition = definition as? IsComparableDefinition<*, *>
        if (comparableDefinition?.unique == true) {
            @Suppress("UNCHECKED_CAST")
            val comparableValue = value as? Comparable<Any>
                ?: throw IllegalStateException("Unique value for ${definition::class} is not comparable")
            handleUnique(reference, comparableValue)
        }
    }
}

private fun <DM : IsRootDataModel> DM.valueToJson(reference: ByteArray, value: Any?): String? {
    if (value == null) return null

    val definition = this.serializableDefinitionFor(reference)

    val builder = StringBuilder()
    val writer = JsonWriter(pretty = false) { part -> builder.append(part) }
    @Suppress("UNCHECKED_CAST")
    (definition as IsSerializablePropertyDefinition<Any, IsPropertyContext?>)
        .writeJsonValue(value, writer, null)

    return builder.toString()
}

private fun <DM : IsRootDataModel> DM.valueFromJson(reference: ByteArray, json: String): Any {
    val definition = this.serializableDefinitionFor(reference)
    val reader = jsonReader(json)
    if (reader.currentToken == JsonToken.StartDocument) {
        reader.nextToken()
    }
    @Suppress("UNCHECKED_CAST")
    return (definition as IsSerializablePropertyDefinition<Any, IsPropertyContext?>)
        .readJson(reader, null)
}

private fun <DM : IsRootDataModel> DM.serializableDefinitionFor(
    reference: ByteArray
): IsSerializablePropertyDefinition<*, *> {
    var index = 0
    val propertyReference = this.getPropertyReferenceByStorageBytes(reference.size, { reference[index++] }, null)
        ?: throw DefNotFoundException("No property reference for provided bytes")

    val definition = propertyReference.propertyDefinition
    return definition as? IsSerializablePropertyDefinition<*, *>
        ?: throw IllegalStateException("Property ${propertyReference.completeName} is not serializable")
}

private fun jsonReader(json: String): JsonReader {
    var index = 0
    return JsonReader {
        json.getOrNull(index++) ?: Char.MIN_VALUE
    }
}

private fun ByteArray.toBase64String(): String = Base64Maryk.encode(this).trimEnd('=')

private fun String.fromBase64String(): ByteArray = Base64Maryk.decode(this)

