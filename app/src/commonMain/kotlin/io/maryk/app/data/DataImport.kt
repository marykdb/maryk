package io.maryk.app.data

import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.emptyValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.references.toListIndex
import maryk.core.properties.types.Key
import maryk.core.properties.types.MutableTypedValue
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.requests.add
import maryk.core.query.responses.AddOrChangeResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.IsAddOrChangeResponseStatus
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.pairs.IsReferenceValueOrNullPair
import maryk.core.query.pairs.ReferenceNullPair
import maryk.core.query.pairs.with
import maryk.core.values.ObjectValues
import maryk.core.values.MutableValueItems
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.rethrowIfFatal
import maryk.file.File
import maryk.json.JsonReader
import maryk.json.JsonToken
import maryk.core.yaml.MarykYamlReader

enum class DataImportScope {
    SINGLE,
    MULTIPLE,
}

internal data class ImportResult(
    val imported: Int,
    val failed: Int,
)

internal suspend fun importDataFromFile(
    dataStore: IsDataStore,
    model: IsRootDataModel,
    format: DataExportFormat,
    scope: DataImportScope,
    path: String,
): ImportResult {
    val requestContext = RequestContext(
        DefinitionsContext(mutableMapOf(model.Meta.name to DataModelReference(model))),
        dataModel = model,
    )
    var imported = 0
    var failed = 0
    val batch = ArrayList<Pair<Key<IsRootDataModel>, Values<IsRootDataModel>>>(100)

    suspend fun flushBatch() {
        if (batch.isEmpty()) return
        val response = dataStore.execute(model.add(*batch.toTypedArray()))
        val (ok, errors) = countStatuses(response.statuses)
        imported += ok
        failed += errors
        batch.clear()
    }

    suspend fun handleRecord(values: ObjectValues<ValuesWithMetaData<*>, ValuesWithMetaData.Companion>) {
        val record = ValuesWithMetaData(values)
        batch.add(record.key to record.values)
        if (batch.size >= 100) {
            flushBatch()
        }
    }

    when (format) {
        DataExportFormat.JSON -> readJsonRecords(path, requestContext, scope, ::handleRecord)
        DataExportFormat.YAML -> readYamlRecords(path, requestContext, scope, ::handleRecord)
        DataExportFormat.PROTO -> readProtoRecords(path, requestContext, scope, ::handleRecord)
    }

    flushBatch()
    return ImportResult(imported = imported, failed = failed)
}

internal suspend fun importVersionedDataFromFile(
    dataStore: IsDataStore,
    model: IsRootDataModel,
    format: DataExportFormat,
    scope: DataImportScope,
    path: String,
): ImportResult {
    val requestContext = RequestContext(
        DefinitionsContext(mutableMapOf(model.Meta.name to DataModelReference(model))),
        dataModel = model,
    )
    var imported = 0
    var failed = 0

    suspend fun handleRecord(values: ObjectValues<DataObjectVersionedChange<*>, DataObjectVersionedChange.Companion>) {
        val record = DataObjectVersionedChange(values)
        val normalized = normalizeVersionedRecord(model, record, requestContext)
        val success = applyVersionedRecord(dataStore, model, normalized)
        if (success) {
            imported += 1
        } else {
            failed += 1
        }
    }

    when (format) {
        DataExportFormat.JSON -> readVersionedJsonRecords(path, requestContext, scope, ::handleRecord)
        DataExportFormat.YAML -> readVersionedYamlRecords(path, requestContext, scope, ::handleRecord)
        DataExportFormat.PROTO -> readVersionedProtoRecords(path, requestContext, scope, ::handleRecord)
    }

    return ImportResult(imported = imported, failed = failed)
}

internal fun detectVersionedImport(
    path: String,
    format: DataExportFormat,
    requestContext: RequestContext,
): Boolean {
    return runCatching {
        when (format) {
            DataExportFormat.JSON -> detectVersionedJson(path, requestContext)
            DataExportFormat.YAML -> detectVersionedYaml(path, requestContext)
            DataExportFormat.PROTO -> detectVersionedProto(path, requestContext)
        }
    }.onFailure {
        it.rethrowIfFatal()
    }.getOrDefault(false)
}

internal fun detectImportFormatFromPath(path: String): DataExportFormat? {
    val extension = path.substringAfterLast('.', "").lowercase()
    if (extension.isNotEmpty()) {
        return when (extension) {
            "json" -> DataExportFormat.JSON
            "yaml", "yml" -> DataExportFormat.YAML
            "proto" -> DataExportFormat.PROTO
            else -> null
        }
    }
    val bytes = readImportBytesOrNull(path) ?: return null
    if (bytes.any { it == 0.toByte() }) return DataExportFormat.PROTO
    val text = bytes.decodeToString()
    val first = text.firstNonWhitespaceChar() ?: return null
    return if (first == '{' || first == '[') DataExportFormat.JSON else DataExportFormat.YAML
}

internal fun detectImportScopeFromPath(path: String, format: DataExportFormat): DataImportScope {
    return when (format) {
        DataExportFormat.JSON -> {
            val content = readImportTextOrNull(path) ?: return DataImportScope.SINGLE
            val first = content.firstNonWhitespaceChar()
            if (first == '[') DataImportScope.MULTIPLE else DataImportScope.SINGLE
        }
        DataExportFormat.YAML -> {
            val content = readImportTextOrNull(path) ?: return DataImportScope.SINGLE
            val docs = splitYamlDocuments(content)
            if (docs.size > 1) DataImportScope.MULTIPLE else DataImportScope.SINGLE
        }
        DataExportFormat.PROTO -> {
            val bytes = readImportBytesOrNull(path) ?: return DataImportScope.SINGLE
            detectProtoScope(bytes)
        }
    }
}

private suspend fun readJsonRecords(
    path: String,
    requestContext: RequestContext,
    scope: DataImportScope,
    onRecord: suspend (ObjectValues<ValuesWithMetaData<*>, ValuesWithMetaData.Companion>) -> Unit,
) {
    val content = readImportText(path)
    val iterator = content.iterator()
    val reader = JsonReader { if (iterator.hasNext()) iterator.nextChar() else null }
    when (scope) {
        DataImportScope.SINGLE -> {
            val values = ValuesWithMetaData.Serializer.readJson(reader, requestContext)
            ensureJsonDocumentEnded(reader)
            onRecord(values)
        }
        DataImportScope.MULTIPLE -> {
            val token = reader.nextToken()
            if (token !is JsonToken.StartArray) {
                throw IllegalArgumentException("Expected JSON array for multiple records.")
            }
            while (true) {
                when (val next = reader.nextToken()) {
                    is JsonToken.EndArray -> {
                        ensureJsonDocumentEnded(reader)
                        return
                    }
                    is JsonToken.ArraySeparator -> continue
                    is JsonToken.StartObject -> {
                        val values = ValuesWithMetaData.Serializer.readJson(reader, requestContext)
                        onRecord(values)
                    }
                    else -> throw IllegalArgumentException("Unexpected JSON token: $next")
                }
            }
        }
    }
}

private suspend fun readYamlRecords(
    path: String,
    requestContext: RequestContext,
    scope: DataImportScope,
    onRecord: suspend (ObjectValues<ValuesWithMetaData<*>, ValuesWithMetaData.Companion>) -> Unit,
) {
    val content = readImportText(path)
    val documents = splitYamlDocuments(content)
    when (scope) {
        DataImportScope.SINGLE -> {
            val doc = documents.firstOrNull() ?: return
            val values = ValuesWithMetaData.Serializer.readJson(MarykYamlReader(doc), requestContext)
            onRecord(values)
        }
        DataImportScope.MULTIPLE -> {
            documents.forEach { doc ->
                val values = ValuesWithMetaData.Serializer.readJson(MarykYamlReader(doc), requestContext)
                onRecord(values)
            }
        }
    }
}

private fun splitYamlDocuments(content: String): List<String> {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return emptyList()
    val raw = trimmed.split(Regex("(?m)^---\\s*$"))
    return raw.map { it.trim() }.filter { it.isNotEmpty() }
}

private const val MAX_IMPORT_FILE_BYTES = 64L * 1024L * 1024L

private fun readImportText(path: String): String =
    readImportTextOrNull(path) ?: throw IllegalArgumentException("File not found: $path")

private fun readImportTextOrNull(path: String): String? {
    return readImportBytesOrNull(path)?.decodeToString()
}

private fun readImportBytes(path: String): ByteArray =
    readImportBytesOrNull(path) ?: throw IllegalArgumentException("File not found: $path")

private fun readImportBytesOrNull(path: String): ByteArray? {
    ensureImportFileSize(path) ?: return null
    val bytes = File.readBytes(path) ?: return null
    if (bytes.size.toLong() > MAX_IMPORT_FILE_BYTES) {
        throw IllegalArgumentException("Import file exceeds max size: ${bytes.size} > $MAX_IMPORT_FILE_BYTES bytes")
    }
    return bytes
}

private fun ensureImportFileSize(path: String): Long? {
    val size = File.size(path) ?: return null
    if (size > MAX_IMPORT_FILE_BYTES) {
        throw IllegalArgumentException("Import file exceeds max size: $size > $MAX_IMPORT_FILE_BYTES bytes")
    }
    return size
}

private suspend fun readProtoRecords(
    path: String,
    requestContext: RequestContext,
    scope: DataImportScope,
    onRecord: suspend (ObjectValues<ValuesWithMetaData<*>, ValuesWithMetaData.Companion>) -> Unit,
) {
    val bytes = readImportBytes(path)
    when (scope) {
        DataImportScope.SINGLE -> {
            val values = readProtoPayload(
                bytes = bytes,
                start = 0,
                length = bytes.size,
                label = "record",
            ) { reader ->
                ValuesWithMetaData.Serializer.readProtoBuf(bytes.size, reader = reader, context = requestContext)
            }
            onRecord(values)
        }
        DataImportScope.MULTIPLE -> {
            var index = 0
            while (index < bytes.size) {
                val frame = readLengthPrefixedFrame(bytes, index)
                index = frame.start
                val values = readProtoPayload(
                    bytes = bytes,
                    start = frame.start,
                    length = frame.length,
                    label = "record at byte ${frame.start}",
                ) { reader ->
                    ValuesWithMetaData.Serializer.readProtoBuf(frame.length, reader = reader, context = requestContext)
                }
                onRecord(values)
                index = frame.end
            }
        }
    }
}

private suspend fun readVersionedJsonRecords(
    path: String,
    requestContext: RequestContext,
    scope: DataImportScope,
    onRecord: suspend (ObjectValues<DataObjectVersionedChange<*>, DataObjectVersionedChange.Companion>) -> Unit,
) {
    val content = readImportText(path)
    val iterator = content.iterator()
    val reader = JsonReader { if (iterator.hasNext()) iterator.nextChar() else null }
    when (scope) {
        DataImportScope.SINGLE -> {
            val values = DataObjectVersionedChange.Serializer.readJson(reader, requestContext)
            ensureJsonDocumentEnded(reader)
            onRecord(values)
        }
        DataImportScope.MULTIPLE -> {
            val token = reader.nextToken()
            if (token !is JsonToken.StartArray) {
                throw IllegalArgumentException("Expected JSON array for multiple records.")
            }
            while (true) {
                when (val next = reader.nextToken()) {
                    is JsonToken.EndArray -> {
                        ensureJsonDocumentEnded(reader)
                        return
                    }
                    is JsonToken.ArraySeparator -> continue
                    is JsonToken.StartObject -> {
                        val values = DataObjectVersionedChange.Serializer.readJson(reader, requestContext)
                        onRecord(values)
                    }
                    else -> throw IllegalArgumentException("Unexpected JSON token: $next")
                }
            }
        }
    }
}

private suspend fun readVersionedYamlRecords(
    path: String,
    requestContext: RequestContext,
    scope: DataImportScope,
    onRecord: suspend (ObjectValues<DataObjectVersionedChange<*>, DataObjectVersionedChange.Companion>) -> Unit,
) {
    val content = readImportText(path)
    val documents = splitYamlDocuments(content)
    when (scope) {
        DataImportScope.SINGLE -> {
            val doc = documents.firstOrNull() ?: return
            val values = DataObjectVersionedChange.Serializer.readJson(MarykYamlReader(doc), requestContext)
            onRecord(values)
        }
        DataImportScope.MULTIPLE -> {
            documents.forEach { doc ->
                val values = DataObjectVersionedChange.Serializer.readJson(MarykYamlReader(doc), requestContext)
                onRecord(values)
            }
        }
    }
}

private suspend fun readVersionedProtoRecords(
    path: String,
    requestContext: RequestContext,
    scope: DataImportScope,
    onRecord: suspend (ObjectValues<DataObjectVersionedChange<*>, DataObjectVersionedChange.Companion>) -> Unit,
) {
    val bytes = readImportBytes(path)
    when (scope) {
        DataImportScope.SINGLE -> {
            val values = readProtoPayload(
                bytes = bytes,
                start = 0,
                length = bytes.size,
                label = "versioned record",
            ) { reader ->
                DataObjectVersionedChange.Serializer.readProtoBuf(
                    bytes.size,
                    reader = reader,
                    context = requestContext
                )
            }
            onRecord(values)
        }
        DataImportScope.MULTIPLE -> {
            var index = 0
            while (index < bytes.size) {
                val frame = readLengthPrefixedFrame(bytes, index)
                index = frame.start
                val values = readProtoPayload(
                    bytes = bytes,
                    start = frame.start,
                    length = frame.length,
                    label = "versioned record at byte ${frame.start}",
                ) { reader ->
                    DataObjectVersionedChange.Serializer.readProtoBuf(
                        frame.length,
                        reader = reader,
                        context = requestContext
                    )
                }
                onRecord(values)
                index = frame.end
            }
        }
    }
}

internal fun ensureJsonDocumentEnded(reader: JsonReader) {
    when (val token = reader.nextToken()) {
        is JsonToken.EndDocument -> Unit
        else -> throw IllegalArgumentException("Unexpected JSON token after records: $token")
    }
}

private fun detectVersionedJson(path: String, requestContext: RequestContext): Boolean {
    val content = readImportTextOrNull(path) ?: return false
    val iterator = content.iterator()
    val reader = JsonReader { if (iterator.hasNext()) iterator.nextChar() else null }
    val token = reader.nextToken()
    return when (token) {
        is JsonToken.StartArray -> {
            when (val next = reader.nextToken()) {
                is JsonToken.EndArray -> false
                is JsonToken.StartObject -> runCatching {
                    DataObjectVersionedChange.Serializer.readJson(reader, requestContext)
                    true
                }.onFailure {
                    it.rethrowIfFatal()
                }.getOrDefault(false)
                is JsonToken.ArraySeparator -> false
                else -> false
            }
        }
        is JsonToken.StartObject -> runCatching {
            DataObjectVersionedChange.Serializer.readJson(reader, requestContext)
            true
        }.onFailure {
            it.rethrowIfFatal()
        }.getOrDefault(false)
        else -> false
    }
}

private fun detectVersionedYaml(path: String, requestContext: RequestContext): Boolean {
    val content = readImportTextOrNull(path) ?: return false
    val doc = splitYamlDocuments(content).firstOrNull() ?: return false
    return runCatching {
        DataObjectVersionedChange.Serializer.readJson(MarykYamlReader(doc), requestContext)
        true
    }.onFailure {
        it.rethrowIfFatal()
    }.getOrDefault(false)
}

private fun detectVersionedProto(path: String, requestContext: RequestContext): Boolean {
    val bytes = readImportBytesOrNull(path) ?: return false
    val read = readVarInt(bytes, 0)
    val (length, offset) = if (read != null && read.value > 0 && read.value <= bytes.size - read.bytesRead) {
        read.value to read.bytesRead
    } else {
        bytes.size to 0
    }
    return runCatching {
        readProtoPayload(
            bytes = bytes,
            start = offset,
            length = length,
            label = "versioned record",
        ) { reader ->
            DataObjectVersionedChange.Serializer.readProtoBuf(length, reader = reader, context = requestContext)
        }
        true
    }.onFailure {
        it.rethrowIfFatal()
    }.getOrDefault(false)
}

private suspend fun applyVersionedRecord(
    dataStore: IsDataStore,
    model: IsRootDataModel,
    record: DataObjectVersionedChange<IsRootDataModel>,
): Boolean {
    val lastVersion = record.changes.maxOfOrNull { it.version } ?: return false
    val update = InitialChangesUpdate(lastVersion, listOf(record))
    val response = dataStore.execute(UpdateResponse(model, update))
    val result = response.result as? AddOrChangeResponse<IsRootDataModel> ?: return false
    return result.statuses.all { it.isSuccessStatus() }
}

private fun normalizeVersionedRecord(
    model: IsRootDataModel,
    record: DataObjectVersionedChange<IsRootDataModel>,
    requestContext: RequestContext,
): DataObjectVersionedChange<IsRootDataModel> {
    if (record.changes.isEmpty()) return record
    val sorted = record.changes.sortedBy { it.version }
    val first = sorted.first()
    val snapshotItems = buildSnapshotItems(model, first.changes, requestContext)
    val materialized = materializeItemsForModel(model, snapshotItems, requestContext)
    val firstChange = valuesToTopLevelChange(model, materialized)
    val normalizedChanges = buildList {
        add(
            VersionedChanges(
                first.version,
                buildList {
                    add(ObjectCreate)
                    firstChange?.let { add(it) }
                }
            )
        )
        addAll(sorted.drop(1))
    }
    return record.copy(changes = normalizedChanges)
}

private fun buildSnapshotItems(
    model: IsRootDataModel,
    changes: List<IsChange>,
    requestContext: RequestContext,
): MutableValueItems {
    val items = MutableValueItems()
    changes.forEach { change ->
        when (change) {
            is Change -> change.referenceValuePairs.forEach { pair ->
                applyReferenceValue(items, pair.reference, pair.value, pair is ReferenceNullPair, requestContext)
            }
            is SetChange -> change.setValueChanges.forEach { setChange ->
                val setValues = setChange.addValues?.toMutableSet() ?: mutableSetOf()
                applyReferenceValue(items, setChange.reference, setValues, false, requestContext)
            }
            else -> Unit
        }
    }
    return items
}

private fun applyReferenceValue(
    items: MutableValueItems,
    reference: AnyPropertyReference,
    value: Any?,
    delete: Boolean,
    requestContext: RequestContext,
) {
    val refs = reference.unwrap(mutableListOf())
    var current: Any = items
    refs.forEachIndexed { index, ref ->
        val isLast = index == refs.lastIndex
        val nextRef = refs.getOrNull(index + 1)
        when (ref) {
            is IsPropertyReferenceForValues<*, *, *, *> -> {
                val target = currentItems(current) ?: return
                if (isLast) {
                    if (delete) {
                        target.remove(ref.index)
                    } else if (value != null) {
                        target[ref.index] = value
                    }
                    return
                }
                val existing = target[ref.index]
                if (existing == null) {
                    val created = createContainerForDefinition(ref.propertyDefinition.definition, nextRef) ?: return
                    target[ref.index] = created
                    current = created
                } else {
                    current = existing
                }
            }
            is ListItemReference<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val list = current as? MutableList<Any?> ?: return
                val listIndex = ref.index.toListIndex()
                if (isLast) {
                    if (delete) {
                        if (listIndex >= list.size) return
                        list.removeAt(listIndex)
                    } else {
                        ensureListSize(list, listIndex, ref.listDefinition.maxSize)
                        list[listIndex] = value
                    }
                    return
                }
                ensureListSize(list, listIndex, ref.listDefinition.maxSize)
                val existing = list[listIndex]
                if (existing == null) {
                    val created = createContainerForDefinition(ref.listDefinition.valueDefinition, nextRef) ?: return
                    list[listIndex] = created
                    current = created
                } else {
                    current = existing
                }
            }
            is MapValueReference<*, *, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = current as? MutableMap<Any?, Any?> ?: return
                val key = ref.key
                if (isLast) {
                    if (delete) {
                        map.remove(key)
                    } else {
                        map[key] = value
                    }
                    return
                }
                val existing = map[key]
                if (existing == null) {
                    val created = createContainerForDefinition(ref.mapDefinition.valueDefinition, nextRef) ?: return
                    map[key] = created
                    current = created
                } else {
                    current = existing
                }
            }
            is SetItemReference<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val set = current as? MutableSet<Any?> ?: return
                if (delete) {
                    set.remove(ref.value)
                } else {
                    set.add(ref.value)
                }
                return
            }
            is TypedValueReference<*, *, *> -> {
                @Suppress("UNCHECKED_CAST")
                val typed = current as? MutableTypedValue<TypeEnum<Any>, Any> ?: return
                if (isLast) {
                    if (value != null) {
                        typed.value = value
                    }
                    return
                }
                val existing = typed.value
                if (existing == Unit) {
                    val created = createContainerForDefinition(ref.propertyDefinition, nextRef) ?: return
                    typed.value = created
                    current = created
                } else {
                    current = existing
                }
            }
            else -> return
        }
        current = when (current) {
            is EmbeddedBuffer -> current.items
            else -> current
        }
    }
}

private fun createContainerForDefinition(
    definition: Any,
    nextRef: IsPropertyReference<*, *, *>?,
): Any? {
    return when (definition) {
        is IsEmbeddedValuesDefinition<*, *> -> EmbeddedBuffer(definition.dataModel, MutableValueItems())
        is IsEmbeddedDefinition<*> -> when (val dataModel = definition.dataModel) {
            is IsValuesDataModel -> EmbeddedBuffer(dataModel, MutableValueItems())
            else -> null
        }
        is IsListDefinition<*, *> -> mutableListOf<Any?>()
        is IsSetDefinition<*, *> -> mutableSetOf<Any?>()
        is IsMapDefinition<*, *, *> -> mutableMapOf<Any?, Any?>()
        is IsMultiTypeDefinition<*, *, *> -> {
            val typedRef = nextRef as? TypedValueReference<*, *, *>
            if (typedRef != null) {
                val type = typedRef.type
                val nested = createContainerForDefinition(typedRef.propertyDefinition, null)
                MutableTypedValue(type, nested ?: Unit)
            } else {
                null
            }
        }
        else -> null
    }
}

private const val MAX_MATERIALIZED_IMPORT_LIST_ITEMS = 100_000

private fun ensureListSize(list: MutableList<Any?>, index: Int, maxSize: UInt?) {
    if (index < 0) {
        throw IllegalArgumentException("Invalid list index in imported data.")
    }
    val limit = maxSize?.let {
        minOf(it.toLong(), MAX_MATERIALIZED_IMPORT_LIST_ITEMS.toLong())
    } ?: MAX_MATERIALIZED_IMPORT_LIST_ITEMS.toLong()
    if (index.toLong() >= limit) {
        throw IllegalArgumentException("List index $index exceeds import materialization limit $limit.")
    }
    while (list.size <= index) {
        list.add(null)
    }
}

private fun currentItems(value: Any): MutableValueItems? =
    when (value) {
        is MutableValueItems -> value
        is EmbeddedBuffer -> value.items
        else -> null
    }

private fun materializeItemsForModel(
    model: IsValuesDataModel,
    items: MutableValueItems,
    requestContext: RequestContext,
): MutableValueItems {
    val materialized = MutableValueItems()
    items.forEach { item ->
        val value = materializeValue(item.value, requestContext)
        if (value != Unit && value != null) {
            materialized[item.index] = value
        }
    }
    return materialized
}

private fun materializeValue(
    value: Any?,
    requestContext: RequestContext,
): Any? {
    return when (value) {
        is EmbeddedBuffer -> {
            val materialized = materializeItemsForModel(value.dataModel, value.items, requestContext)
            value.dataModel.emptyValues().copy(materialized)
        }
        is MutableList<*> -> value.map { item -> materializeValue(item, requestContext) }.toMutableList()
        is MutableSet<*> -> value.mapNotNull { item -> materializeValue(item, requestContext) }.toMutableSet()
        is MutableMap<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val map = value as MutableMap<Any?, Any?>
            map.entries.associate { (k, v) ->
                k to materializeValue(v, requestContext)
            }.toMutableMap()
        }
        is MutableTypedValue<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val typed = value as MutableTypedValue<TypeEnum<Any>, Any>
            val materialized = materializeValue(value.value, requestContext)
            typed.value = materialized ?: Unit
            typed
        }
        else -> value
    }
}

private fun valuesToTopLevelChange(
    model: IsRootDataModel,
    values: MutableValueItems,
): Change? {
    val pairs = mutableListOf<IsReferenceValueOrNullPair<Any>>()
    values.forEach { item ->
        val value = item.value
        if (value == Unit) return@forEach
        val def = model[item.index] ?: return@forEach
        @Suppress("UNCHECKED_CAST")
        val ref = def.ref(null) as IsPropertyReference<Any, IsValueDefinitionWrapper<Any, *, IsPropertyContext, *>, *>
        pairs += ref with value
    }
    return if (pairs.isEmpty()) null else Change(*pairs.toTypedArray())
}

private data class EmbeddedBuffer(
    val dataModel: IsValuesDataModel,
    val items: MutableValueItems,
)

private fun countStatuses(statuses: List<IsAddResponseStatus<IsRootDataModel>>): Pair<Int, Int> {
    var ok = 0
    var failed = 0
    statuses.forEach { status ->
        if (status is AddSuccess) ok += 1 else failed += 1
    }
    return ok to failed
}

private fun IsAddOrChangeResponseStatus<IsRootDataModel>.isSuccessStatus(): Boolean {
    return this is AddSuccess || this is ChangeSuccess
}

private data class VarIntRead(
    val value: Int,
    val bytesRead: Int,
)

private data class ProtoFrame(
    val length: Int,
    val start: Int,
    val end: Int,
)

private fun readVarInt(bytes: ByteArray, startIndex: Int): VarIntRead? {
    var shift = 0
    var result = 0
    var index = startIndex
    while (index < bytes.size && shift < 32) {
        val b = bytes[index].toInt() and 0xFF
        if (shift == 28 && (b and 0xF0) != 0) return null
        result = result or ((b and 0x7F) shl shift)
        index += 1
        if (b and 0x80 == 0) {
            return VarIntRead(result, index - startIndex)
        }
        shift += 7
    }
    return null
}

private fun readLengthPrefixedFrame(bytes: ByteArray, index: Int): ProtoFrame {
    val read = readVarInt(bytes, index)
        ?: throw IllegalArgumentException("Invalid proto length at byte $index.")
    if (read.value <= 0) {
        throw IllegalArgumentException("Invalid proto length at byte $index.")
    }

    val frameStart = index + read.bytesRead
    if (read.value > bytes.size - frameStart) {
        throw IllegalArgumentException("Invalid proto length at byte $index.")
    }

    return ProtoFrame(
        length = read.value,
        start = frameStart,
        end = frameStart + read.value,
    )
}

internal inline fun <T> readProtoPayload(
    bytes: ByteArray,
    start: Int,
    length: Int,
    label: String,
    read: (() -> Byte) -> T,
): T {
    require(start >= 0 && start <= bytes.size) { "Invalid proto $label start." }
    require(length >= 0 && length <= bytes.size - start) { "Invalid proto $label length." }
    var index = start
    val end = start + length
    val value = try {
        read {
            if (index >= end) {
                throw IllegalArgumentException("Invalid proto $label: attempted to read past $length bytes.")
            }
            bytes[index++]
        }
    } catch (error: Throwable) {
        error.rethrowIfFatal()
        throw IllegalArgumentException("Invalid proto $label.", error)
    }
    if (index != end) {
        throw IllegalArgumentException("Invalid proto $label: consumed ${index - start} of $length bytes.")
    }
    return value
}

private fun String.firstNonWhitespaceChar(): Char? = firstOrNull { !it.isWhitespace() }

private fun detectProtoScope(bytes: ByteArray): DataImportScope {
    val frameCount = countLengthPrefixedFrames(bytes) ?: return DataImportScope.SINGLE
    // A single valid frame is ambiguous with regular protobuf payload bytes.
    // Prefer SINGLE unless at least two framed records are present.
    return if (frameCount > 1) DataImportScope.MULTIPLE else DataImportScope.SINGLE
}

private fun countLengthPrefixedFrames(bytes: ByteArray): Int? {
    if (bytes.isEmpty()) return null

    var index = 0
    var frameCount = 0
    while (index < bytes.size) {
        val frame = runCatching {
            readLengthPrefixedFrame(bytes, index)
        }.onFailure {
            it.rethrowIfFatal()
        }.getOrNull() ?: return null

        frameCount += 1
        index = frame.end
    }
    return if (index == bytes.size) frameCount else null
}
