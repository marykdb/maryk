package io.maryk.cli

import io.maryk.cli.commands.FakeDataStore
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse
import maryk.file.File
import maryk.test.models.SimpleMarykModel
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecordSubcommandRunnerTest {
    @Test
    fun saveSubcommandReturnsErrorWhenSaveThrows() {
        val result = runRecordSubcommand(
            tokens = listOf("save", "out", "--kotlin", "--package", "test"),
            saveContext = SaveContext(
                key = "record",
                dataYaml = "",
                dataJson = "",
                dataProto = ByteArray(0),
                metaYaml = "",
                metaJson = "",
                metaProto = ByteArray(0),
                kotlinGenerator = { throw IllegalStateException("boom") },
            ),
            loadContext = null,
            deleteContext = null,
        )

        val error = assertIs<RecordSubcommandResult.Error>(result)
        assertEquals("Save failed: boom", error.message)
    }

    @Test
    fun loadSubcommandReturnsErrorWhenApplyThrows() {
        val values = SimpleMarykModel.create {
            value with "updated"
        }
        val key = SimpleMarykModel.key(values)
        val yaml = buildString {
            val writer = YamlWriter { append(it) }
            SimpleMarykModel.Serializer.writeJson(values, writer)
        }
        val path = "build/tmp/record-subcommand-load.yaml"
        File.writeText(path, yaml)

        val store = object : FakeDataStore(
            dataModelsById = mapOf(1u to SimpleMarykModel),
        ) {
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                throw IllegalStateException("boom")
            }
        }

        val result = runRecordSubcommand(
            tokens = listOf("load", path, "--yaml"),
            saveContext = null,
            loadContext = LoadContext(
                label = "SimpleMarykModel $key",
                dataModel = SimpleMarykModel,
                key = key,
                dataStore = store,
            ),
            deleteContext = null,
        )

        val error = assertIs<RecordSubcommandResult.Error>(result)
        assertTrue(error.message.startsWith("Load failed: boom"))
    }
}
