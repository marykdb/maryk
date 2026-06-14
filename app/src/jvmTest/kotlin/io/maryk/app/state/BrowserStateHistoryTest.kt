package io.maryk.app.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.shared.IsDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowserStateHistoryTest {
    @Test
    fun loadAllChangesFallsBackToSingleVersionRequest() {
        val values = HistoryModel.create {
            id with 7u
            number with 42u
        }
        val key = HistoryModel.key(values)
        var calls = 0
        val store = object : IsDataStore {
            override val dataModelsById: Map<UInt, IsRootDataModel> = mapOf(1u to HistoryModel)
            override val dataModelIdsByString: Map<String, UInt> = mapOf(HistoryModel.Meta.name to 1u)
            override val keepAllVersions: Boolean = true
            override val keepUpdateHistoryIndex: Boolean = false
            override val supportsFuzzyQualifierFiltering: Boolean = false
            override val supportsSubReferenceFiltering: Boolean = false

            @Suppress("UNCHECKED_CAST")
            override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
                request: RQ,
            ): RP {
                return when (request) {
                    is GetChangesRequest<*> -> {
                        calls += 1
                        if (request.maxVersions > 1u) {
                            throw IllegalStateException("backend only accepts maxVersions=1")
                        }
                        val version = when (request.fromVersion) {
                            0uL -> 1uL
                            2uL -> 2uL
                            else -> null
                        }
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

        val changes = runBlocking {
            loadAllChanges(store, HistoryModel, key)
        }

        assertEquals(6, calls)
        assertEquals(2, changes.size)
        assertEquals(listOf(1uL, 2uL), changes.map { it.version })
        assertTrue(changes.all { it.changes.contains(ObjectCreate) })
    }
}

private object HistoryModel : RootDataModel<HistoryModel>(
    keyDefinition = {
        HistoryModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val number by number(index = 2u, type = UInt32)
}
