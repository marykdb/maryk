package io.maryk.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.shared.IsDataStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class DataExportVersionHistoryTest {
    @Test
    fun exportModelDataWithVersionHistoryWritesHistoricChanges() {
        val values = ExportHistoryModel.create {
            id with 7u
            number with 42u
        }
        val key = ExportHistoryModel.key(values)
        val store = object : IsDataStore {
            override val dataModelsById: Map<UInt, IsRootDataModel> = mapOf(1u to ExportHistoryModel)
            override val dataModelIdsByString: Map<String, UInt> = mapOf(ExportHistoryModel.Meta.name to 1u)
            override val keepAllVersions: Boolean = true
            override val keepUpdateHistoryIndex: Boolean = false
            override val supportsFuzzyQualifierFiltering: Boolean = false
            override val supportsSubReferenceFiltering: Boolean = false

            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                return when (request) {
                    is ScanRequest<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val scanValues = listOf(
                            ValuesWithMetaData(
                                key = key,
                                values = values,
                                firstVersion = 1uL,
                                lastVersion = 1uL,
                                isDeleted = false,
                            )
                        ) as List<ValuesWithMetaData<DM>>
                        ValuesResponse(
                            dataModel = request.dataModel,
                            values = scanValues,
                        ) as RP
                    }
                    is GetChangesRequest<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        ChangesResponse(
                            dataModel = request.dataModel,
                            changes = listOf(
                                DataObjectVersionedChange(
                                    key = key,
                                    changes = listOf(
                                        VersionedChanges(
                                            version = 1uL,
                                            changes = listOf(ObjectCreate),
                                        )
                                    )
                                )
                            )
                        ) as RP
                    }
                    else -> throw NotImplementedError("Unexpected request: ${request::class.simpleName}")
                }
            }

            override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
                request: RQ,
            ): Flow<IsUpdateResponse<DM>> = throw NotImplementedError("Not used")

            override suspend fun <DM : IsRootDataModel> processUpdate(
                updateResponse: UpdateResponse<DM>,
            ): ProcessResponse<DM> = throw NotImplementedError("Not used")

            override suspend fun close() = Unit
            override suspend fun closeAllListeners() = Unit
        }

        val folder = Files.createTempDirectory("maryk-export-history-")
        try {
            runBlocking {
                exportModelDataToFolder(
                    dataStore = store,
                    model = ExportHistoryModel,
                    format = DataExportFormat.YAML,
                    folder = folder.toString(),
                    includeVersionHistory = true,
                )
            }

            val output = Files.readString(folder.resolve("${ExportHistoryModel.Meta.name}.data.versions.yaml"))
            assertTrue(output.contains("changes:"))
            assertTrue(output.contains("!ObjectCreate"))
        } finally {
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun exportModelDataWithVersionHistoryFallsBackToSingleVersionRequest() {
        val values = ExportHistoryModel.create {
            id with 7u
            number with 42u
        }
        val key = ExportHistoryModel.key(values)
        val store = object : IsDataStore {
            override val dataModelsById: Map<UInt, IsRootDataModel> = mapOf(1u to ExportHistoryModel)
            override val dataModelIdsByString: Map<String, UInt> = mapOf(ExportHistoryModel.Meta.name to 1u)
            override val keepAllVersions: Boolean = true
            override val keepUpdateHistoryIndex: Boolean = false
            override val supportsFuzzyQualifierFiltering: Boolean = false
            override val supportsSubReferenceFiltering: Boolean = false

            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                return when (request) {
                    is ScanRequest<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        ValuesResponse(
                            dataModel = request.dataModel,
                            values = listOf(
                                ValuesWithMetaData(
                                    key = key,
                                    values = values,
                                    firstVersion = 1uL,
                                    lastVersion = 1uL,
                                    isDeleted = false,
                                )
                            ) as List<ValuesWithMetaData<DM>>,
                        ) as RP
                    }
                    is GetChangesRequest<*> -> {
                        if (request.maxVersions > 1u) {
                            throw IllegalStateException("backend only accepts maxVersions=1")
                        }
                        val version = when (request.fromVersion) {
                            0uL -> 1uL
                            2uL -> 2uL
                            else -> null
                        }
                        @Suppress("UNCHECKED_CAST")
                        ChangesResponse(
                            dataModel = request.dataModel,
                            changes = version?.let {
                                listOf(
                                    DataObjectVersionedChange(
                                        key = key,
                                        changes = listOf(
                                            VersionedChanges(
                                                version = it,
                                                changes = listOf(ObjectCreate),
                                            )
                                        ),
                                    )
                                )
                            }.orEmpty(),
                        ) as RP
                    }
                    else -> throw NotImplementedError("Unexpected request: ${request::class.simpleName}")
                }
            }

            override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
                request: RQ,
            ): Flow<IsUpdateResponse<DM>> = throw NotImplementedError("Not used")

            override suspend fun <DM : IsRootDataModel> processUpdate(
                updateResponse: UpdateResponse<DM>,
            ): ProcessResponse<DM> = throw NotImplementedError("Not used")

            override suspend fun close() = Unit
            override suspend fun closeAllListeners() = Unit
        }

        val folder = Files.createTempDirectory("maryk-export-history-fallback-")
        try {
            runBlocking {
                exportModelDataToFolder(
                    dataStore = store,
                    model = ExportHistoryModel,
                    format = DataExportFormat.YAML,
                    folder = folder.toString(),
                    includeVersionHistory = true,
                )
            }

            val output = Files.readString(folder.resolve("${ExportHistoryModel.Meta.name}.data.versions.yaml"))
            assertTrue(output.contains("changes:"))
            assertTrue(output.contains("version: 1"))
            assertTrue(output.contains("version: 2"))
        } finally {
            folder.toFile().deleteRecursively()
        }
    }
}

private object ExportHistoryModel : RootDataModel<ExportHistoryModel>(
    keyDefinition = {
        ExportHistoryModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val number by number(index = 2u, type = UInt32)
}
