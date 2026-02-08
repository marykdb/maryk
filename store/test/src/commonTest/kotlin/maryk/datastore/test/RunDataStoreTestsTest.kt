package maryk.datastore.test

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.IsFetchRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.shared.IsDataStore
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunDataStoreTestsTest {
    @Test
    fun throwsOnUnknownRunOnlyTest() = runTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            runDataStoreTests(NoOpDataStore, runOnlyTest = "doesNotExist")
        }

        assertTrue(exception.message?.contains("No datastore test found") == true)
    }
}

private object NoOpDataStore : IsDataStore {
    override val dataModelsById: Map<UInt, IsRootDataModel> = emptyMap()
    override val dataModelIdsByString: Map<String, UInt> = emptyMap()
    override val keepAllVersions: Boolean = true
    override val supportsFuzzyQualifierFiltering: Boolean = false
    override val supportsSubReferenceFiltering: Boolean = false

    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ,
    ): RP {
        throw NotImplementedError("No-op datastore should not execute requests")
    }

    override suspend fun <DM : IsRootDataModel, RQ : IsFetchRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
        request: RQ,
    ): Flow<IsUpdateResponse<DM>> = emptyFlow()

    override suspend fun <DM : IsRootDataModel> processUpdate(
        updateResponse: UpdateResponse<DM>,
    ): ProcessResponse<DM> {
        throw NotImplementedError("No-op datastore should not process updates")
    }

    override suspend fun close() = Unit

    override suspend fun closeAllListeners() = Unit
}
