package maryk.datastore.test

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
                printFailureStackTraces = false,
            )
        }

        assertTrue(secondTestRan)
    }

    @Test
    fun timesOutResetAndContinuesWithTheNextCase() = runTest {
        var secondTestRan = false
        var resetCount = 0
        val testClass = object : IsDataStoreTest {
            override val allTests: Map<String, suspend () -> Any> = linkedMapOf(
                "first" to { Unit },
                "next" to {
                    secondTestRan = true
                },
            )

            override suspend fun resetData() {
                resetCount += 1
                if (resetCount == 1) {
                    delay(1.seconds)
                }
            }
        }

        val exception = assertFailsWith<RuntimeException> {
            runDataStoreTestClasses(
                dataStore = NoOpDataStore,
                testClasses = arrayOf("ResetTest" to { testClass }),
                caseTimeout = 1.milliseconds,
                printFailureStackTraces = false,
            )
        }

        assertTrue(secondTestRan)
        assertTrue(exception.message?.contains("ResetTest.first.reset") == true)
    }

    @Test
    fun isolatedSelectionRequiresEveryQualifiedTestIdentifier() = runTest {
        var selectedTestRan = false
        val testClass = object : IsDataStoreTest {
            override val allTests: Map<String, suspend () -> Any> = mapOf(
                "selected" to {
                    selectedTestRan = true
                },
            )

            override suspend fun resetData() = Unit
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            runDataStoreTestClassesIsolated(
                createDataStore = { NoOpDataStore },
                testClasses = arrayOf("SelectionTest" to { testClass }),
                runOnlyTests = setOf("SelectionTest.selected", "SelectionTest.unknown"),
            )
        }

        assertTrue(exception.message?.contains("SelectionTest.unknown") == true)
        assertTrue(!selectedTestRan)
    }

    @Test
    fun isolatedSelectionRejectsAnAmbiguousQualifiedTestIdentifier() = runTest {
        var selectedTestRan = false
        val testClass = object : IsDataStoreTest {
            override val allTests: Map<String, suspend () -> Any> = mapOf(
                "selected" to {
                    selectedTestRan = true
                },
            )

            override suspend fun resetData() = Unit
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            runDataStoreTestClassesIsolated(
                createDataStore = { NoOpDataStore },
                testClasses = arrayOf(
                    "DuplicateTest" to { testClass },
                    "DuplicateTest" to { testClass },
                ),
                runOnlyTests = setOf("DuplicateTest.selected"),
            )
        }

        assertTrue(exception.message?.contains("DuplicateTest.selected") == true)
        assertTrue(!selectedTestRan)
    }

    @Test
    fun isolatedSelectionRunsOnlyTheExactlyQualifiedTest() = runTest {
        var selectedTestRan = false
        var unselectedTestRan = false
        val testClass = object : IsDataStoreTest {
            override val allTests: Map<String, suspend () -> Any> = mapOf(
                "selected" to {
                    selectedTestRan = true
                },
                "unselected" to {
                    unselectedTestRan = true
                },
            )

            override suspend fun resetData() = Unit
        }

        runDataStoreTestClassesIsolated(
            createDataStore = { NoOpDataStore },
            testClasses = arrayOf("SelectionTest" to { testClass }),
            runOnlyTests = setOf("SelectionTest.selected"),
        )

        assertTrue(selectedTestRan)
        assertTrue(!unselectedTestRan)
    }

    @Test
    fun isolatedTimeoutBoundsResetAndCloseWithoutHidingTheTestFailure() = runTest {
        var resetStarted = false
        var createCount = 0
        val stores = mutableListOf<SlowClosingDataStore>()
        val testClass = object : IsDataStoreTest {
            override val allTests: Map<String, suspend () -> Any> = mapOf(
                "stalled" to {
                    delay(1.seconds)
                },
            )

            override suspend fun resetData() {
                resetStarted = true
                delay(1.seconds)
            }
        }

        val exception = assertFailsWith<RuntimeException> {
            runDataStoreTestClassesIsolated(
                createDataStore = {
                    createCount += 1
                    if (createCount == 1) NoOpDataStore else SlowClosingDataStore().also(stores::add)
                },
                testClasses = arrayOf("TimeoutTest" to { testClass }),
                runOnlyTests = setOf("TimeoutTest.stalled"),
                caseTimeout = 1.milliseconds,
                printFailureStackTraces = false,
            )
        }

        assertTrue(resetStarted)
        assertTrue(stores.last().closeStarted)
        assertTrue(exception.message?.contains("TimeoutTest.stalled.test") == true)
        assertTrue(exception.message?.contains("TimeoutTest.stalled.reset") == true)
        assertTrue(exception.message?.contains("TimeoutTest.stalled.close") == true)
        assertTrue(exception.cause?.message?.contains("timed out after 1ms") == true)
    }

    @Test
    fun isolatedDiscoveryCloseIsBoundedAndReported() = runTest {
        var createCount = 0
        val testClass = object : IsDataStoreTest {
            override val allTests: Map<String, suspend () -> Any> = mapOf("selected" to { Unit })

            override suspend fun resetData() = Unit
        }

        val exception = assertFailsWith<RuntimeException> {
            runDataStoreTestClassesIsolated(
                createDataStore = {
                    createCount += 1
                    if (createCount == 1) SlowClosingDataStore() else NoOpDataStore
                },
                testClasses = arrayOf("DiscoveryTest" to { testClass }),
                runOnlyTests = setOf("DiscoveryTest.selected"),
                caseTimeout = 1.milliseconds,
                printFailureStackTraces = false,
            )
        }

        assertTrue(exception.message?.contains("DiscoveryTest.discovery.close") == true)
    }

    @Test
    fun propagatesParentCancellationInsteadOfReportingItAsACaseFailure() = runTest {
        val cancellation = CancellationException("cancel parent")
        val parent = Job()
        var resetRan = false
        val testClass = object : IsDataStoreTest {
            override val allTests: Map<String, suspend () -> Any> = mapOf(
                "cancel" to {
                    parent.cancel(cancellation)
                    delay(1.seconds)
                },
            )

            override suspend fun resetData() {
                resetRan = true
            }
        }

        val exception = assertFailsWith<CancellationException> {
            withContext(parent) {
                runDataStoreTestClasses(
                    dataStore = NoOpDataStore,
                    testClasses = arrayOf("CancellationTest" to { testClass }),
                )
            }
        }

        assertTrue(exception.message == cancellation.message)
        assertTrue(resetRan)
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

private class SlowClosingDataStore : IsDataStore by NoOpDataStore {
    var closeStarted = false

    override suspend fun close() {
        closeStarted = true
        delay(1.seconds)
    }
}
