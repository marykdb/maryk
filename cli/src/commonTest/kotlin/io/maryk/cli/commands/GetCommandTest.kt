package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.RocksDbStoreConnection
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.values.Values
import maryk.test.models.SimpleMarykModel
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetCommandTest {
    private val environment = object : CliEnvironment {
        override fun resolveDirectory(path: String): DirectoryResolution = DirectoryResolution.Success(path)
    }

    @Test
    fun errorsWhenNotConnected() {
        val state = CliState()

        val result = GetCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            emptyList(),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("Not connected"))
    }

    @Test
    fun showsUsageWhenMissingArguments() {
        val store = FakeDataStore()
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = GetCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().startsWith("Usage"))
    }

    @Test
    fun errorsOnUnknownModel() {
        val store = FakeDataStore()
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = GetCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("MissingModel", "abc"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("Unknown model"))
    }

    @Test
    fun errorsOnInvalidKey() {
        val store = FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = GetCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", "abc"),
        )

        assertTrue(result.isError)
        assertTrue(result.lines.first().startsWith("Invalid key"))
    }

    @Test
    fun retrievesDataAndPrintsYaml() {
        val values = SimpleMarykModel.create {
            value with "hello"
        }
        val keyString = SimpleMarykModel.key(values).toString()

        val yamlLines = buildList {
            val builder = StringBuilder()
            val writer = YamlWriter { builder.append(it) }
            SimpleMarykModel.Serializer.writeJson(values, writer)
            addAll(builder.toString().trimEnd().lines())
        }

        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                val getRequest = request as GetRequest<DM>
                val response = ValuesResponse(
                    dataModel = getRequest.dataModel,
                    values = listOf(
                        ValuesWithMetaData(
                            key = getRequest.keys.single(),
                            values = values as Values<DM>,
                            firstVersion = 1uL,
                            lastVersion = 1uL,
                            isDeleted = false,
                        ),
                    ),
                )
                @Suppress("UNCHECKED_CAST")
                return response as RP
            }
        }
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = GetCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", keyString),
        )

        assertFalse(result.isError)
        assertEquals(
            listOf(
                "Model: SimpleMarykModel",
                "Key: $keyString",
                "First version: 1",
                "Last version: 1",
                "Deleted: false",
                "Lines: ${yamlLines.size}",
                "----- Data -----",
            ) + yamlLines + listOf("----- End of record: SimpleMarykModel $keyString -----"),
            result.lines,
        )
    }

    @Test
    fun includeDeletedDisablesSoftDeleteFilter() {
        val values = SimpleMarykModel.create {
            value with "hello"
        }
        val keyString = SimpleMarykModel.key(values).toString()

        var captured: GetRequest<IsRootDataModel>? = null
        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                captured = request as GetRequest<IsRootDataModel>
                val response = ValuesResponse(
                    dataModel = request.dataModel,
                    values = emptyList(),
                )
                @Suppress("UNCHECKED_CAST")
                return response as RP
            }
        }
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = GetCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", keyString, "--include-deleted"),
        )

        val request = requireNotNull(captured)
        assertFalse(request.filterSoftDeleted)
        assertTrue(result.isError)
        assertTrue(result.lines.first().contains("No data found"))
    }
}
