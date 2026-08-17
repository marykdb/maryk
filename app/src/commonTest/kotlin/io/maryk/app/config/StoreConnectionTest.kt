package io.maryk.app.config

import kotlinx.coroutines.flow.Flow
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.shared.IsDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StoreConnectionTest {
    @Test
    fun closeReportsListenerFailureAndStillClosesStore() {
        val store = FailingCloseStore()
        val connection = StoreConnection(
            StoreDefinition("id", "store", StoreKind.REMOTE, "https://store.example.test"),
            store,
        )

        val error = assertFailsWith<IllegalStateException> {
            connection.close()
        }

        assertEquals("listener close boom", error.message)
        assertTrue(store.closed)
    }
}

private class FailingCloseStore : IsDataStore {
    override val dataModelsById = emptyMap<UInt, IsRootDataModel>()
    override val dataModelIdsByString = emptyMap<String, UInt>()
    override val keepAllVersions = false
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = false
    override val supportsSubReferenceFiltering = false
    var closed = false

    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(request: RQ): RP =
        error("Not used")

    override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
        request: RQ,
    ): Flow<IsUpdateResponse<DM>> = error("Not used")

    override suspend fun <DM : IsRootDataModel> processUpdate(updateResponse: UpdateResponse<DM>): ProcessResponse<DM> =
        error("Not used")

    override suspend fun closeAllListeners() {
        throw IllegalStateException("listener close boom")
    }

    override suspend fun close() {
        closed = true
    }
}
