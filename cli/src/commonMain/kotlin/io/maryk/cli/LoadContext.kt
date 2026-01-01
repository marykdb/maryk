package io.maryk.cli

import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.models.asValues
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import maryk.core.yaml.MarykYamlReader
import maryk.datastore.shared.IsDataStore
import maryk.file.File
import maryk.json.JsonReader
import maryk.yaml.YamlWriter

data class ApplyResult(
    val message: String,
    val success: Boolean,
)

sealed class RefreshResult {
    data class Success(
        val lines: List<String>,
        val saveContext: SaveContext,
    ) : RefreshResult()

    data class Error(
        val message: String,
    ) : RefreshResult()
}

data class LoadContext(
    val label: String,
    val dataModel: IsRootDataModel,
    val key: Key<IsRootDataModel>,
    val dataStore: IsDataStore,
) {
    fun load(
        path: String,
        format: SaveFormat,
        ifVersion: ULong? = null,
        useMeta: Boolean = false,
    ): String {
        return loadResult(path, format, ifVersion, useMeta).message
    }

    fun loadResult(
        path: String,
        format: SaveFormat,
        ifVersion: ULong? = null,
        useMeta: Boolean = false,
    ): ApplyResult {
        if (format == SaveFormat.KOTLIN) {
            return ApplyResult("Kotlin input is not supported.", success = false)
        }

        val loaded = try {
            readValues(path, format, useMeta)
        } catch (e: Throwable) {
            return ApplyResult(
                "Load failed: ${e.message ?: e::class.simpleName}",
                success = false,
            )
        }

        val meta = loaded.meta
        if (meta != null && meta.key != key) {
            return ApplyResult(
                "Load failed: metadata key does not match current record.",
                success = false,
            )
        }

        val guardVersion = ifVersion ?: meta?.lastVersion

        val values = loaded.values
        val changes = values.toChanges()
        if (changes.isEmpty()) {
            return ApplyResult("No data loaded from $path.", success = false)
        }

        return applyChangesResult(
            changes = changes.toList(),
            ifVersion = guardVersion,
            action = "Loaded",
            target = path,
        )
    }

    fun resolveReference(path: String): IsPropertyReference<*, IsPropertyDefinition<*>, *> {
        val context = createRequestContext()
        return dataModel.getPropertyReferenceByName(path, context)
    }

    fun parseValueForReference(
        reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>,
        rawValue: String,
    ): Any {
        return parseValueForDefinition(reference.propertyDefinition, rawValue, reference)
    }

    fun parseValueForDefinition(
        definition: IsPropertyDefinition<*>,
        rawValue: String,
        reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>? = null,
    ): Any {
        val baseDefinition = when (definition) {
            is IsValueDefinitionWrapper<*, *, *, *> -> definition.definition
            else -> definition
        }
        if (baseDefinition is StringDefinition) {
            return rawValue
        }

        @Suppress("UNCHECKED_CAST")
        val serializable = definition as? IsSerializablePropertyDefinition<Any, IsPropertyContext>
            ?: throw IllegalArgumentException("Property is not a serializable value.")

        val context = createRequestContext(reference)
        val trimmed = rawValue.trim()
        val allowSimpleFallback = baseDefinition is IsSimpleValueDefinition<*, *> &&
            trimmed.isNotEmpty() &&
            trimmed.first() != '{' &&
            trimmed.first() != '['

        return try {
            val reader = MarykYamlReader(rawValue)
            serializable.readJson(reader, context)
        } catch (e: Throwable) {
            if (allowSimpleFallback) {
                @Suppress("UNCHECKED_CAST")
                val simple = baseDefinition as IsSimpleValueDefinition<Any, IsPropertyContext>
                return simple.fromString(rawValue, context)
            }
            throw e
        }
    }

    fun applyChangesResult(
        changes: List<maryk.core.query.changes.IsChange>,
        ifVersion: ULong? = null,
        action: String = "Updated",
        target: String? = null,
    ): ApplyResult {
        if (changes.isEmpty()) {
            return ApplyResult("No changes to apply.", success = false)
        }

        val request = dataModel.change(key.change(*changes.toTypedArray(), lastVersion = ifVersion))
        val response = runBlocking { dataStore.execute(request) }
        return formatStatus(action, target, response)
    }

    fun refreshView(): RefreshResult {
        val response = runBlocking {
            dataStore.execute(dataModel.get(key))
        }

        val valuesWithMetaData = response.values.firstOrNull()
            ?: return RefreshResult.Error(
                "No data found for key ${key.toString()} in ${dataModel.Meta.name}."
            )

        val values = valuesWithMetaData.values
        @Suppress("UNCHECKED_CAST")
        val serializer = dataModel.Serializer as IsDataModelSerializer<
            Values<IsRootDataModel>,
            IsRootDataModel,
            IsPropertyContext
            >
        val yaml = buildString {
            val writer = YamlWriter { append(it) }
            serializer.writeJson(values, writer)
        }

        val yamlLines = sanitizeOutput(yaml)
            .trimEnd()
            .lineSequence()
            .map { line -> line.filter { it == '\t' || it >= ' ' } }
            .toList()

        val keyToken = key.toString()
        val lines = buildList {
            add("Model: ${dataModel.Meta.name}")
            add("Key: $keyToken")
            add("First version: ${valuesWithMetaData.firstVersion}")
            add("Last version: ${valuesWithMetaData.lastVersion}")
            add("Deleted: ${valuesWithMetaData.isDeleted}")
            add("Lines: ${yamlLines.size}")
            add("----- Data -----")
            if (yamlLines.isEmpty()) {
                add("<no data>")
            } else {
                addAll(yamlLines)
            }
            add("----- End of record: ${dataModel.Meta.name} $keyToken -----")
        }

        val saveContext = buildSaveContext(
            dataModel = dataModel,
            keyToken = keyToken,
            values = values,
            valuesWithMetaData = valuesWithMetaData,
        )

        return RefreshResult.Success(lines, saveContext)
    }

    private data class LoadedValues(
        val values: Values<IsRootDataModel>,
        val meta: ValuesWithMetaData<IsRootDataModel>? = null,
    )

    private fun readValues(path: String, format: SaveFormat, useMeta: Boolean): LoadedValues {
        return if (useMeta) {
            readMetaValues(path, format)
        } else {
            LoadedValues(readDataValues(path, format))
        }
    }

    private fun readDataValues(path: String, format: SaveFormat): Values<IsRootDataModel> {
        @Suppress("UNCHECKED_CAST")
        val serializer = dataModel.Serializer as IsDataModelSerializer<
            Values<IsRootDataModel>,
            IsRootDataModel,
            IsPropertyContext
        >

        return when (format) {
            SaveFormat.YAML -> {
                val content = File.readText(path) ?: throw IllegalArgumentException("File not found: $path")
                serializer.readJson(MarykYamlReader(content), null)
            }
            SaveFormat.JSON -> {
                val content = File.readText(path) ?: throw IllegalArgumentException("File not found: $path")
                val iterator = content.iterator()
                val reader = JsonReader { if (iterator.hasNext()) iterator.nextChar() else Char.MIN_VALUE }
                serializer.readJson(reader, null)
            }
            SaveFormat.PROTO -> {
                val bytes = File.readBytes(path) ?: throw IllegalArgumentException("File not found: $path")
                var index = 0
                serializer.readProtoBuf(bytes.size, reader = { bytes[index++] }, context = null)
            }
            SaveFormat.KOTLIN -> throw IllegalArgumentException("Kotlin input is not supported.")
        }
    }

    private fun readMetaValues(path: String, format: SaveFormat): LoadedValues {
        val requestContext = createRequestContext()
        val metaValues = when (format) {
            SaveFormat.YAML -> {
                val content = File.readText(path) ?: throw IllegalArgumentException("File not found: $path")
                ValuesWithMetaData.Serializer.readJson(MarykYamlReader(content), requestContext)
            }
            SaveFormat.JSON -> {
                val content = File.readText(path) ?: throw IllegalArgumentException("File not found: $path")
                val iterator = content.iterator()
                val reader = JsonReader { if (iterator.hasNext()) iterator.nextChar() else Char.MIN_VALUE }
                ValuesWithMetaData.Serializer.readJson(reader, requestContext)
            }
            SaveFormat.PROTO -> {
                val bytes = File.readBytes(path) ?: throw IllegalArgumentException("File not found: $path")
                var index = 0
                ValuesWithMetaData.Serializer.readProtoBuf(bytes.size, reader = { bytes[index++] }, context = requestContext)
            }
            SaveFormat.KOTLIN -> throw IllegalArgumentException("Kotlin input is not supported.")
        }

        val meta = ValuesWithMetaData(metaValues)
        return LoadedValues(meta.values, meta)
    }

    private fun createRequestContext(
        reference: IsPropertyReference<*, IsPropertyDefinition<*>, *>? = null,
    ): RequestContext {
        val definitionsContext = DefinitionsContext(
            mutableMapOf(dataModel.Meta.name to DataModelReference(dataModel))
        )
        return RequestContext(definitionsContext, dataModel = dataModel).also {
            @Suppress("UNCHECKED_CAST")
            it.reference = reference as? IsPropertyReference<*, IsSerializablePropertyDefinition<*, *>, *>
        }
    }

    private fun formatStatus(
        action: String,
        target: String?,
        response: ChangeResponse<IsRootDataModel>,
    ): ApplyResult {
        val status = response.statuses.firstOrNull()
            ?: return ApplyResult("$action failed: no response status for $label.", success = false)
        val targetLabel = target?.let { " from $it" }.orEmpty()
        return when (status) {
            is ChangeSuccess -> ApplyResult(
                "$action $label$targetLabel (version ${status.version}).",
                success = true,
            )
            is DoesNotExist -> ApplyResult(
                "$action failed: $label does not exist.",
                success = false,
            )
            is ValidationFail -> {
                val details = status.exceptions.joinToString(separator = "; ") { exception ->
                    exception.message ?: exception.toString()
                }
                ApplyResult("$action failed: $details", success = false)
            }
            is ServerFail -> ApplyResult("$action failed: ${status.reason}", success = false)
            else -> ApplyResult("$action failed: ${status.statusType}", success = false)
        }
    }

    private fun buildSaveContext(
        dataModel: IsRootDataModel,
        keyToken: String,
        values: Values<IsRootDataModel>,
        valuesWithMetaData: ValuesWithMetaData<IsRootDataModel>,
    ): SaveContext {
        @Suppress("UNCHECKED_CAST")
        val serializer = dataModel.Serializer as IsDataModelSerializer<
            Values<IsRootDataModel>,
            IsRootDataModel,
            IsPropertyContext
            >

        val dataYaml = buildString {
            val writer = YamlWriter { append(it) }
            serializer.writeJson(values, writer)
        }
        val dataJson = serializer.writeJson(values)
        val dataProto = writeDataProto(serializer, values)

        val definitionsContext = DefinitionsContext(
            mutableMapOf(dataModel.Meta.name to DataModelReference(dataModel))
        )
        val requestContext = RequestContext(definitionsContext, dataModel = dataModel)
        val metaValues = ValuesWithMetaData.asValues(valuesWithMetaData, requestContext)

        val metaYaml = buildString {
            val writer = YamlWriter { append(it) }
            ValuesWithMetaData.Serializer.writeJson(metaValues, writer, requestContext)
        }
        val metaJson = ValuesWithMetaData.Serializer.writeJson(metaValues, requestContext)
        val metaProto = writeMetaProto(metaValues, requestContext)

        return SaveContext(
            key = keyToken,
            dataYaml = dataYaml,
            dataJson = dataJson,
            dataProto = dataProto,
            metaYaml = metaYaml,
            metaJson = metaJson,
            metaProto = metaProto,
        )
    }

    private fun writeDataProto(
        serializer: IsDataModelSerializer<Values<IsRootDataModel>, IsRootDataModel, IsPropertyContext>,
        values: Values<IsRootDataModel>,
    ): ByteArray {
        val cache = WriteCache()
        val length = serializer.calculateProtoBufLength(values, cache, null)
        val bytes = ByteArray(length)
        var index = 0
        serializer.writeProtoBuf(values, cache, { bytes[index++] = it }, null)
        return bytes
    }

    private fun writeMetaProto(
        valuesWithMetaData: ObjectValues<ValuesWithMetaData<*>, ValuesWithMetaData.Companion>,
        context: RequestContext,
    ): ByteArray {
        val cache = WriteCache()
        val length = ValuesWithMetaData.Serializer.calculateProtoBufLength(valuesWithMetaData, cache, context)
        val bytes = ByteArray(length)
        var index = 0
        ValuesWithMetaData.Serializer.writeProtoBuf(valuesWithMetaData, cache, { bytes[index++] = it }, context)
        return bytes
    }

    private fun sanitizeOutput(value: String): String {
        if (value.isEmpty()) return value
        val withoutAnsi = ANSI_ESCAPE.replace(value, "")
        return withoutAnsi.filter { char ->
            char == '\n' || char == '\t' || !char.isISOControl()
        }
    }

    private companion object {
        private val ANSI_ESCAPE = Regex(
            "\\u001B\\[[0-?]*[ -/]*[@-~]|\\u009B[0-?]*[ -/]*[@-~]"
        )
    }
}
