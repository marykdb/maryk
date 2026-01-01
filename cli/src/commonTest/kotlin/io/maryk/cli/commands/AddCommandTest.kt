package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.RocksDbStoreConnection
import maryk.core.models.IsRootDataModel
import maryk.core.models.asValues
import maryk.core.models.key
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.changes.IsChange
import maryk.core.values.Values
import maryk.file.File
import maryk.test.models.SimpleMarykModel
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AddCommandTest {
    private val environment = object : CliEnvironment {
        override fun resolveDirectory(path: String): DirectoryResolution = DirectoryResolution.Success(path)
    }

    @Test
    fun errorsWhenNotConnected() {
        val state = CliState()

        val result = AddCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", "./record.yaml"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("Not connected"))
    }

    @Test
    fun addsRecordWithExplicitKey() {
        val values = SimpleMarykModel.create {
            value with "hello"
        }
        val yaml = buildString {
            val writer = YamlWriter { append(it) }
            SimpleMarykModel.Serializer.writeJson(values, writer)
        }

        val path = "build/tmp/add-command.yaml"
        File.writeText(path, yaml)

        val keyToken = SimpleMarykModel.key(values).toString()

        var captured: AddRequest<IsRootDataModel>? = null
        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                val addRequest = request as AddRequest<DM>
                captured = addRequest as AddRequest<IsRootDataModel>
                val key = addRequest.keysForObjects?.first()
                    ?: addRequest.dataModel.key(addRequest.objects.first())
                val response = AddResponse(
                    dataModel = addRequest.dataModel,
                    statuses = listOf(AddSuccess(key = key, version = 1uL, changes = emptyList<IsChange>())),
                )
                return response as RP
            }
        }
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = AddCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", path, "--key", keyToken),
        )

        val request = assertNotNull(captured)
        assertEquals(keyToken, request.keysForObjects?.single()?.toString())
        assertTrue(values == request.objects.single())
        assertFalse(result.isError)
        assertEquals(listOf("Added SimpleMarykModel $keyToken (version 1)."), result.lines)
    }

    @Test
    fun addsRecordFromMetaFile() {
        val values = SimpleMarykModel.create {
            value with "meta"
        }
        val key = SimpleMarykModel.key(values)
        val meta = ValuesWithMetaData(
            key = key,
            values = values,
            firstVersion = 1uL,
            lastVersion = 2uL,
            isDeleted = false,
        )

        val requestContext = RequestContext(
            DefinitionsContext(mutableMapOf(SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel))),
            dataModel = SimpleMarykModel,
        )
        val metaValues = ValuesWithMetaData.asValues(meta, requestContext)
        val yaml = buildString {
            val writer = YamlWriter { append(it) }
            ValuesWithMetaData.Serializer.writeJson(metaValues, writer, requestContext)
        }

        val path = "build/tmp/add-command-meta.yaml"
        File.writeText(path, yaml)

        var captured: AddRequest<IsRootDataModel>? = null
        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                val addRequest = request as AddRequest<DM>
                captured = addRequest as AddRequest<IsRootDataModel>
                val response = AddResponse(
                    dataModel = addRequest.dataModel,
                    statuses = listOf(AddSuccess(key = key, version = 1uL, changes = emptyList<IsChange>())),
                )
                return response as RP
            }
        }
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = AddCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", path, "--meta"),
        )

        val request = assertNotNull(captured)
        assertEquals(key, request.keysForObjects?.single())
        assertTrue(values == request.objects.single())
        assertFalse(result.isError)
        assertEquals(listOf("Added SimpleMarykModel ${key.toString()} (version 1)."), result.lines)
    }

    @Test
    fun addsMultipleRecordsFromYamlList() {
        val first = SimpleMarykModel.create {
            value with "one"
        }
        val second = SimpleMarykModel.create {
            value with "two"
        }
        val yaml = buildYamlList(first, second)

        val path = "build/tmp/add-command-list.yaml"
        File.writeText(path, yaml)

        val key1 = SimpleMarykModel.key(ByteArray(16) { 1 })
        val key2 = SimpleMarykModel.key(ByteArray(16) { 2 })

        var captured: AddRequest<IsRootDataModel>? = null
        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                val addRequest = request as AddRequest<DM>
                captured = addRequest as AddRequest<IsRootDataModel>
                val response = AddResponse(
                    dataModel = addRequest.dataModel,
                    statuses = listOf(
                        AddSuccess(key = key1, version = 1uL, changes = emptyList<IsChange>()),
                        AddSuccess(key = key2, version = 2uL, changes = emptyList<IsChange>()),
                    ),
                )
                return response as RP
            }
        }
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = AddCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", path),
        )

        val request = assertNotNull(captured)
        assertEquals(2, request.objects.size)
        assertEquals(null, request.keysForObjects)
        assertFalse(result.isError)
        assertEquals(2, result.lines.size)
        assertTrue(result.lines[0].startsWith("Added SimpleMarykModel "))
        assertTrue(result.lines[1].startsWith("Added SimpleMarykModel "))
    }

    private fun buildYamlList(
        vararg values: Values<SimpleMarykModel>,
    ): String {
        val items = values.map { item ->
            buildString {
                val writer = YamlWriter { append(it) }
                SimpleMarykModel.Serializer.writeJson(item, writer)
            }.trimEnd()
        }

        return items.joinToString("\n") { item ->
            val lines = item.lineSequence().toList()
            if (lines.isEmpty()) {
                "- {}"
            } else {
                lines.mapIndexed { index, line ->
                    if (index == 0) "- $line" else "  $line"
                }.joinToString("\n")
            }
        }
    }
}
