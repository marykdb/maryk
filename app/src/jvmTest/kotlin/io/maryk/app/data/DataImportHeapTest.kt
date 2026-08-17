package io.maryk.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.add
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.memory.InMemoryDataStore
import maryk.datastore.shared.IsDataStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DataImportHeapTest {
    @Test
    fun importsMaximumSizeFileWithinConstrainedHeap() {
        val java = System.getProperty("java.home") + "/bin/java"
        val process = ProcessBuilder(
            java,
            "-Xmx256m",
            "-cp",
            System.getProperty("java.class.path"),
            DataImportHeapProbe::class.java.name,
        ).inheritIO().start()

        assertEquals(0, process.waitFor())
    }
}

object DataImportHeapProbe {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        val source = InMemoryDataStore.open(dataModelsById = mapOf(1u to HeapImportModel))
        val folder = Files.createTempDirectory("maryk-import-heap-")
        try {
            val values = HeapImportModel.create {
                id with 1u
                number with 1u
            }
            source.execute(HeapImportModel.add(values))
            exportRowDataToFolder(
                dataStore = source,
                model = HeapImportModel,
                key = HeapImportModel.key(values),
                keyText = "1",
                format = DataExportFormat.JSON,
                folder = folder.toString(),
            )
            val record = Files.readString(folder.resolve("${HeapImportModel.Meta.name}.1.json"))
            val path = folder.resolve("records.json")
            Files.newBufferedWriter(path).use { writer ->
                writer.append('[')
                var first = true
                var written = 1L
                while (written + record.length + 1 < 63L * 1024L * 1024L) {
                    if (!first) writer.append(',')
                    writer.append(record)
                    written += record.length + if (first) 0 else 1
                    first = false
                }
                writer.append(']')
            }

            importDataFromFile(
                dataStore = CountingDataStore,
                model = HeapImportModel,
                format = DataExportFormat.JSON,
                scope = DataImportScope.MULTIPLE,
                path = path.toString(),
            )
        } finally {
            source.close()
            folder.toFile().deleteRecursively()
        }
    }
}

private object HeapImportModel : RootDataModel<HeapImportModel>(
    keyDefinition = { HeapImportModel.run { id.ref() } },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val number by number(index = 2u, type = UInt32)
}

private object CountingDataStore : IsDataStore {
    override val dataModelsById = mapOf<UInt, IsRootDataModel>(1u to HeapImportModel)
    override val dataModelIdsByString = mapOf(HeapImportModel.Meta.name to 1u)
    override val keepAllVersions = false
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = false
    override val supportsSubReferenceFiltering = false

    @Suppress("UNCHECKED_CAST")
    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ,
    ): RP {
        check((request as AddRequest<*>).objects.size <= 100)
        return AddResponse(request.dataModel, emptyList()) as RP
    }

    override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
        request: RQ,
    ): Flow<IsUpdateResponse<DM>> = throw NotImplementedError()

    override suspend fun <DM : IsRootDataModel> processUpdate(
        updateResponse: UpdateResponse<DM>,
    ): ProcessResponse<DM> = throw NotImplementedError()

    override suspend fun close() = Unit
    override suspend fun closeAllListeners() = Unit
}
