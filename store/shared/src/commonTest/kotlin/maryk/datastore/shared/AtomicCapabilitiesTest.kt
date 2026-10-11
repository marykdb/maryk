package maryk.datastore.shared

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.requests.add
import maryk.core.query.responses.AtomicResponse
import maryk.core.query.responses.AtomicStatus
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtomicCapabilitiesTest {
    @Test
    fun reportedLimitsControlExecutionWithoutLegacyFallback() = runTest {
        var executed = false
        fun store(limits: AtomicCapabilities): IsDataStore = object : IsDataStore by NoOpStore, AtomicDataStore {
            override val supportsAtomicRequests = true
            override val atomicCapabilities = limits
            override suspend fun executeAtomic(request: AtomicRequest): AtomicResponse {
                executed = true
                return AtomicResponse(AtomicStatus.Committed)
            }
        }
        val limited = store(AtomicCapabilities(maxMutations = 2u))
        val request = AtomicRequest("reported-limit", List(3) { SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-$it" }) })
        val rejected = limited.executeAtomic(request)
        assertEquals(AtomicStatus.Rejected, rejected.status)
        assertEquals("mutations", rejected.failure?.limit)
        assertEquals(2uL, rejected.failure?.maximum)
        assertEquals(3uL, rejected.failure?.actual)
        assertFalse(executed)

        val standard = store(AtomicCapabilities())
        assertEquals(1000u, standard.atomicCapabilities?.maxMutations)
        val larger = AtomicRequest("current-default", List(101) { SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-$it" }) })
        assertEquals(AtomicStatus.Committed, standard.executeAtomic(larger).status)
        assertTrue(executed)
    }
}

private object NoOpStore : IsDataStore {
    override val dataModelsById = mapOf(1u to SimpleMarykModel)
    override val dataModelIdsByString = mapOf(SimpleMarykModel.Meta.name to 1u)
    override val keepAllVersions = false
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = false
    override val supportsSubReferenceFiltering = false
    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(request: RQ): RP = error("Unexpected execute")
    override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(request: RQ): Flow<IsUpdateResponse<DM>> = error("Unexpected flow")
    override suspend fun <DM : IsRootDataModel> processUpdate(updateResponse: UpdateResponse<DM>): ProcessResponse<DM> = error("Unexpected update")
    override suspend fun close() {}
    override suspend fun closeAllListeners() {}
}
