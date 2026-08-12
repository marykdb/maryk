package maryk.datastore.test

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.getUpdates
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RunDataStoreTestsTest {
    @Test
    fun throwsOnUnknownRunOnlyTest() = runTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            runDataStoreTests(NoOpDataStore, runOnlyTest = "doesNotExist")
        }

        assertTrue(exception.message?.contains("No datastore test found") == true)
    }

    @Test
    fun timesOutOneCaseAndContinuesWithTheNext() = runTest {
        var secondTestRan = false
        val testClass = object : IsDataStoreTest {
            override val allTests: Map<String, suspend () -> Any> = linkedMapOf(
                "stalled" to {
                    delay(1.seconds)
                },
                "next" to {
                    secondTestRan = true
                },
            )

            override suspend fun resetData() = Unit
        }

        assertFailsWith<RuntimeException> {
            runDataStoreTestClasses(
                dataStore = NoOpDataStore,
                testClasses = arrayOf("TimeoutTest" to { testClass }),
                caseTimeout = 1.milliseconds,
            )
        }

        assertTrue(secondTestRan)
    }

    @Test
    fun listenerFailureIsReportedToCallingTest() = runTest {
        assertFailsWith<AssertionError> {
            updateListenerTester(
                dataStore = ExtraUpdateDataStore,
                request = SimpleMarykModel.getUpdates(),
                responseCount = 1,
            ) { responses ->
                responses.single().await()
                delay(100.milliseconds)
            }
        }
    }
}

private object NoOpDataStore : IsDataStore {
    override val dataModelsById: Map<UInt, IsRootDataModel> = emptyMap()
    override val dataModelIdsByString: Map<String, UInt> = emptyMap()
    override val keepAllVersions: Boolean = true
    override val keepUpdateHistoryIndex: Boolean = false
    override val supportsFuzzyQualifierFiltering: Boolean = false
    override val supportsSubReferenceFiltering: Boolean = false

    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(
        request: RQ,
    ): RP {
        throw NotImplementedError("No-op datastore should not execute requests")
    }

    override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
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

private object ExtraUpdateDataStore : IsDataStore by NoOpDataStore {
    override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
        request: RQ,
    ): Flow<IsUpdateResponse<DM>> = flow {
        @Suppress("UNCHECKED_CAST")
        val update = InitialValuesUpdate<SimpleMarykModel>(version = 1uL, values = emptyList()) as IsUpdateResponse<DM>
        emit(update)
        emit(update)
    }
}
