package io.maryk.cli

import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.Key
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.Values
import maryk.core.yaml.MarykYamlReader
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.datastore.shared.IsDataStore
import maryk.file.File
import maryk.json.JsonReader

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
        if (format == SaveFormat.KOTLIN) {
            return "Kotlin input is not supported."
        }

        val loaded = try {
            readValues(path, format, useMeta)
        } catch (e: Throwable) {
            return "Load failed: ${e.message ?: e::class.simpleName}"
        }

        val meta = loaded.meta
        if (meta != null && meta.key != key) {
            return "Load failed: metadata key does not match current record."
        }

        val guardVersion = ifVersion ?: meta?.lastVersion

        val values = loaded.values
        val changes = values.toChanges()
        if (changes.isEmpty()) {
            return "No data loaded from $path."
        }

        val request = dataModel.change(key.change(*changes, lastVersion = guardVersion))
        val response = runBlocking { dataStore.execute(request) }
        return formatStatus(path, response)
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

    private fun createRequestContext(): RequestContext {
        val definitionsContext = DefinitionsContext(
            mutableMapOf(dataModel.Meta.name to DataModelReference(dataModel))
        )
        return RequestContext(definitionsContext, dataModel = dataModel)
    }

    private fun formatStatus(path: String, response: ChangeResponse<IsRootDataModel>): String {
        val status = response.statuses.firstOrNull()
            ?: return "Load failed: no response status for $label."
        return when (status) {
            is ChangeSuccess -> "Loaded $label from $path (version ${status.version})."
            is DoesNotExist -> "Load failed: $label does not exist."
            is ValidationFail -> {
                val details = status.exceptions.joinToString(separator = "; ") { exception ->
                    exception.message ?: exception.toString()
                }
                "Load failed: $details"
            }
            is ServerFail -> "Load failed: ${status.reason}"
            else -> "Load failed: ${status.statusType}"
        }
    }
}
