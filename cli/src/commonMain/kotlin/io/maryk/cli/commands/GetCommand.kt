package io.maryk.cli.commands

import kotlinx.coroutines.runBlocking
import maryk.core.models.asValues
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.get
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import io.maryk.cli.DeleteContext
import io.maryk.cli.SaveContext
import maryk.yaml.YamlWriter
import maryk.core.query.requests.delete

class GetCommand : Command {
    override val name: String = "get"
    override val description: String = "Get a record by base64 key and show it in YAML."

    override fun execute(context: CommandContext, arguments: List<String>): CommandResult {
        val connection = context.state.currentConnection
            ?: return CommandResult(
                lines = listOf("Not connected to any store. Use `connect` first."),
                isError = true,
            )

        if (arguments.size < 2) {
            return CommandResult(
                lines = listOf(
                    "Usage: get <model> <base64-key>",
                    "Example: get SimpleMarykModel AbCdEf123",
                ),
                isError = true,
            )
        }

        val dataStore = connection.dataStore
        val modelToken = arguments[0]
        val keyToken = arguments[1]

        val dataModel = resolveDataModel(dataStore, modelToken)
            ?: return CommandResult(
                lines = listOf(
                    "Unknown model `$modelToken`.",
                    "Run `list` to see available models.",
                ),
                isError = true,
            )

        val key = try {
            dataModel.key(keyToken)
        } catch (e: Throwable) {
            return CommandResult(
                lines = listOf("Invalid key: ${e.message ?: e::class.simpleName}"),
                isError = true,
            )
        }

        val request: GetRequest<IsRootDataModel> = dataModel.get(key)
        val response = runBlocking {
            dataStore.execute(request)
        }

        val valuesWithMetaData = response.values.firstOrNull()
            ?: return CommandResult(
                lines = listOf("No data found for key $keyToken in ${dataModel.Meta.name}."),
                isError = true,
            )

        val yamlBuilder = StringBuilder()
        val yamlWriter = YamlWriter { yamlBuilder.append(it) }
        val values = valuesWithMetaData.values
        @Suppress("UNCHECKED_CAST")
        val serializer = dataModel.Serializer as IsDataModelSerializer<Values<IsRootDataModel>, IsRootDataModel, IsPropertyContext>
        serializer.writeJson(values, yamlWriter)

        val yamlLines = sanitizeOutput(yamlBuilder.toString())
            .trimEnd()
            .lineSequence()
            .map { line -> line.filter { it == '\t' || it >= ' ' } }
            .toList()

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

        val deleteContext = DeleteContext(
            label = "${dataModel.Meta.name} $keyToken",
        ) { hardDelete ->
            val request = dataModel.delete(key, hardDelete = hardDelete)
            runBlocking { dataStore.execute(request) }
            if (hardDelete) {
                listOf("Hard deleted ${dataModel.Meta.name} $keyToken.")
            } else {
                listOf("Deleted ${dataModel.Meta.name} $keyToken.")
            }
        }

        return CommandResult(
            lines = lines,
            saveContext = saveContext,
            deleteContext = deleteContext,
        )
    }

    private fun resolveDataModel(
        dataModelHolder: DataModelHolder,
        token: String,
    ): IsRootDataModel? {
        val byName = dataModelHolder.dataModelIdsByString[token]?.let { dataModelHolder.dataModelsById[it] }
        if (byName != null) {
            return byName
        }

        val numericId = token.toUIntOrNull()
        return numericId?.let { dataModelHolder.dataModelsById[it] }
    }

    private fun sanitizeOutput(value: String): String {
        if (value.isEmpty()) return value
        val withoutAnsi = ANSI_ESCAPE.replace(value, "")
        return withoutAnsi.filter { char ->
            char == '\n' || char == '\t' || !char.isISOControl()
        }
    }

    private fun buildSaveContext(
        dataModel: IsRootDataModel,
        keyToken: String,
        values: Values<IsRootDataModel>,
        valuesWithMetaData: ValuesWithMetaData<IsRootDataModel>,
    ): SaveContext {
        @Suppress("UNCHECKED_CAST")
        val serializer = dataModel.Serializer as IsDataModelSerializer<Values<IsRootDataModel>, IsRootDataModel, IsPropertyContext>

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

    private companion object {
        private val ANSI_ESCAPE = Regex(
            "\\u001B\\[[0-?]*[ -/]*[@-~]|\\u009B[0-?]*[ -/]*[@-~]"
        )
    }
}

private typealias DataModelHolder = IsDataStore
