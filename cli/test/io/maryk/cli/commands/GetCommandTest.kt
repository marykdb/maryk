package io.maryk.cli.commands

import io.maryk.cli.CliEnvironment
import io.maryk.cli.CliState
import io.maryk.cli.DirectoryResolution
import io.maryk.cli.RocksDbStoreConnection
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.properties.types.Key
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
    fun reportsDataStoreFailure() {
        val values = SimpleMarykModel.create {
            value with "hello"
        }
        val keyString = SimpleMarykModel.key(values).toString()
        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                throw IllegalStateException("boom")
            }
        }
        val state = CliState().apply {
            replaceConnection(RocksDbStoreConnection("/data/store", store))
        }

        val result = GetCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", keyString),
        )

        assertTrue(result.isError)
        assertEquals(listOf("Get failed: boom"), result.lines)
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
    fun deleteSubcommandMarksDoesNotExistAsError() {
        val result = executeDelete { key ->
            DeleteResponse(SimpleMarykModel, listOf(DoesNotExist(key)))
        }

        assertTrue(result.isError)
        assertTrue(result.lines.single().contains("does not exist"))
    }

    @Test
    fun deleteSubcommandMarksServerFailureAsError() {
        val result = executeDelete {
            DeleteResponse(SimpleMarykModel, listOf(ServerFail("backend unavailable")))
        }

        assertTrue(result.isError)
        assertEquals(listOf("Delete failed: backend unavailable"), result.lines)
    }

    @Test
    fun deleteSubcommandMarksEmptyResponseAsError() {
        val result = executeDelete {
            DeleteResponse(SimpleMarykModel, emptyList())
        }

        assertTrue(result.isError)
        assertTrue(result.lines.single().startsWith("Delete failed: no response status"))
    }

    @Test
    fun deleteSubcommandMarksThrownFailureAsError() {
        val result = executeDelete {
            throw IllegalStateException("backend unavailable")
        }

        assertTrue(result.isError)
        assertEquals(listOf("Delete failed: backend unavailable"), result.lines)
    }

    @Test
    fun deleteSubcommandKeepsSuccessfulDeletionNonError() {
        val result = executeDelete {
            DeleteResponse(SimpleMarykModel, listOf(DeleteSuccess(1uL)))
        }

        assertFalse(result.isError)
        assertTrue(result.lines.single().startsWith("Deleted SimpleMarykModel "))
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

    private fun executeDelete(
        deleteResponse: (Key<SimpleMarykModel>) -> DeleteResponse<SimpleMarykModel>,
    ): CommandResult {
        val values = SimpleMarykModel.create { value with "hello" }
        val key = SimpleMarykModel.key(values)
        val store = object : FakeDataStore(dataModelsById = mapOf(1u to SimpleMarykModel)) {
            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(request: RQ): RP = when (request) {
                is GetRequest<*> -> ValuesResponse(
                    dataModel = SimpleMarykModel,
                    values = listOf(ValuesWithMetaData(key, values, 1uL, 1uL, false)),
                ) as RP
                is DeleteRequest<*> -> deleteResponse(key) as RP
                else -> error("Unexpected request")
            }
        }
        val state = CliState().apply { replaceConnection(RocksDbStoreConnection("/data/store", store)) }

        return GetCommand().execute(
            CommandContext(CommandRegistry(state, environment), state, environment),
            listOf("SimpleMarykModel", key.toString(), "delete"),
        )
    }
}
