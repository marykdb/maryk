package maryk.datastore.test

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import maryk.datastore.shared.IsDataStore
import maryk.test.models.AnyValueIncMapIndexModel
import maryk.test.models.AnyValueMapIndexModel
import maryk.test.models.AnyValueSetIndexModel
import maryk.test.models.CaseInsensitivePerson
import maryk.test.models.CompleteMarykModel
import maryk.test.models.ComplexModel
import maryk.test.models.Log
import maryk.test.models.Measurement
import maryk.test.models.ModelV2ExtraIndex
import maryk.test.models.Person
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal typealias DataStoreTestConstructor = (IsDataStore) -> IsDataStoreTest

private val allTestClasses: Array<Pair<String, DataStoreTestConstructor>> = arrayOf(
    "DataStoreAddTest" to ::DataStoreAddTest,
    "DataStoreChangeComplexTest" to ::DataStoreChangeComplexTest,
    "DataStoreChangeTest" to ::DataStoreChangeTest,
    "DataStoreChangeValidationTest" to ::DataStoreChangeValidationTest,
    "DataStoreDeleteTest" to ::DataStoreDeleteTest,
    "DataStoreFilterComplexTest" to ::DataStoreFilterComplexTest,
    "DataStoreFilterTest" to ::DataStoreFilterTest,
    "DataStoreGeoTest" to ::DataStoreGeoTest,
    "DataStoreGetChangesComplexTest" to ::DataStoreGetChangesComplexTest,
    "DataStoreGetChangesTest" to ::DataStoreGetChangesTest,
    "DataStoreGetUpdatesAndFlowTest" to ::DataStoreGetUpdatesAndFlowTest,
    "DataStoreGetTest" to ::DataStoreGetTest,
    "DataStoreGetSelectTest" to ::DataStoreGetSelectTest,
    "DataStoreProcessUpdateTest" to ::DataStoreProcessUpdateTest,
    "DataStoreScanChangesTest" to ::DataStoreScanChangesTest,
    "DataStoreScanMultiTypeTest" to ::DataStoreScanMultiTypeTest,
    "DataStoreScanOnIndexTest" to ::DataStoreScanOnIndexTest,
    "DataStoreScanOnNormalizeIndexTest" to ::DataStoreScanOnNormalizeIndexTest,
    "DataStoreScanOnAnyValueIndexTest" to ::DataStoreScanOnAnyValueIndexTest,
    "DataStoreScanOnIndexWithPersonTest" to ::DataStoreScanOnIndexWithPersonTest,
    "DataStoreScanTest" to ::DataStoreScanTest,
    "DataStoreScanUpdateHistoryTest" to ::DataStoreScanUpdateHistoryTest,
    "DataStoreScanUniqueTest" to ::DataStoreScanUniqueTest,
    "DataStoreScanUpdatesAndFlowTest" to ::DataStoreScanUpdatesAndFlowTest,
    "DataStoreScanUpdatesWithLogTest" to ::DataStoreScanUpdatesWithLogTest,
    "DataStoreScanWithFilterTest" to ::DataStoreScanWithFilterTest,
    "DataStoreScanWithMutableValueIndexTest" to ::DataStoreScanWithMutableValueIndexTest,
    "DataStoreSoftDeleteTimeTravelTest" to ::DataStoreSoftDeleteTimeTravelTest,
    "UniqueTest" to ::UniqueTest,
)

private val testCaseDispatcher = Dispatchers.Default.limitedParallelism(1)

val dataModelsForTests = mapOf(
    1u to TestMarykModel,
    2u to SimpleMarykModel,
    3u to ComplexModel,
    4u to Log,
    5u to CompleteMarykModel,
    6u to UniqueModel,
    7u to ModelV2ExtraIndex,
    8u to Person,
    9u to Measurement,
    10u to AnyValueMapIndexModel,
    11u to AnyValueIncMapIndexModel,
    12u to AnyValueSetIndexModel,
    13u to CaseInsensitivePerson,
    14u to GeoLocation,
)

suspend fun runDataStoreTests(
    dataStore: IsDataStore,
    runOnlyTest: String? = null,
    caseTimeout: Duration = 60.seconds,
) = runDataStoreTestClasses(dataStore, allTestClasses, runOnlyTest, caseTimeout)

internal suspend fun runDataStoreTestClasses(
    dataStore: IsDataStore,
    testClasses: Array<Pair<String, DataStoreTestConstructor>>,
    runOnlyTest: String? = null,
    caseTimeout: Duration = 60.seconds,
) {
    val exceptionList = mutableMapOf<String, Throwable>()
    var executedTests = 0

    for ((testClassName, testClassConstructor) in testClasses) {
        val testClass = testClassConstructor(dataStore)

        var hasPrintedTestClassName = false

        for ((testName, test) in testClass.allTests) {
            if (runOnlyTest != null && testName != runOnlyTest) {
                continue
            }
            if (!hasPrintedTestClassName) {
                println(testClassName)
                hasPrintedTestClassName = true
            }

            println("- $testName")
            executedTests += 1

            var phase = "init"
            try {
                runBoundedCasePhase(caseTimeout, exceptionList, testClassName, testName, { phase }) {
                    testClass.initData()
                    phase = "test"
                    test()
                }
            } finally {
                phase = "reset"
                runBoundedCasePhase(caseTimeout, exceptionList, testClassName, testName, { phase }, cleanup = true) {
                    testClass.resetData()
                }
            }
        }
    }
    if (runOnlyTest != null && executedTests == 0) {
        throw IllegalArgumentException("No datastore test found with name `$runOnlyTest`.")
    }
    if (exceptionList.isNotEmpty()) {
        val messages = StringBuilder("DataStore Tests failed: (${exceptionList.size})[\n")
        var firstThrowable: Throwable? = null
        for ((name, exception) in exceptionList) {
            if (firstThrowable == null) {
                firstThrowable = exception
            }
            messages.append("\t$name: $exception\n")
        }
        messages.append(']')
        throw RuntimeException(messages.toString(), firstThrowable)
    }
}

suspend fun runDataStoreTestsIsolated(
    createDataStore: () -> IsDataStore,
    runOnlyTests: Set<String>? = null,
    caseTimeout: Duration = 60.seconds,
) {
    runDataStoreTestClassesIsolated(createDataStore, allTestClasses, runOnlyTests, caseTimeout)
}

internal suspend fun runDataStoreTestClassesIsolated(
    createDataStore: () -> IsDataStore,
    testClasses: Array<Pair<String, DataStoreTestConstructor>>,
    runOnlyTests: Set<String>? = null,
    caseTimeout: Duration = 60.seconds,
) {
    val exceptionList = mutableMapOf<String, Throwable>()
    val testCases = testClasses.flatMap { (testClassName, testClassConstructor) ->
        val dataStore = createDataStore()
        try {
            testClassConstructor(dataStore).allTests.keys.map { testName ->
                Triple(testClassName, testClassConstructor, testName)
            }
        } finally {
            runBoundedCasePhase(caseTimeout, exceptionList, testClassName, "discovery", { "close" }, cleanup = true) {
                dataStore.close()
            }
        }
    }

    val selectedTestCases = if (runOnlyTests == null) {
        testCases
    } else {
        val unresolvedTestNames = runOnlyTests.filter { requestedTestName ->
            testCases.count { (testClassName, _, testName) ->
                "$testClassName.$testName" == requestedTestName
            } != 1
        }
        require(unresolvedTestNames.isEmpty()) {
            "No datastore test found exactly once with qualified names `${unresolvedTestNames.joinToString()}`."
        }
        testCases.filter { (testClassName, _, testName) ->
            "$testClassName.$testName" in runOnlyTests
        }
    }

    for ((testClassName, testClassConstructor, testName) in selectedTestCases) {
        println(testClassName)
        println("- $testName")

        val dataStore = createDataStore()
        try {
            val testClass = testClassConstructor(dataStore)
            val test = testClass.allTests[testName]
                ?: error("Missing test `$testName` in `$testClassName`.")

            var phase = "init"
            try {
                runBoundedCasePhase(caseTimeout, exceptionList, testClassName, testName, { phase }) {
                    testClass.initData()
                    phase = "test"
                    test()
                }
            } finally {
                phase = "reset"
                runBoundedCasePhase(caseTimeout, exceptionList, testClassName, testName, { phase }, cleanup = true) {
                    testClass.resetData()
                }
            }
        } finally {
            runBoundedCasePhase(caseTimeout, exceptionList, testClassName, testName, { "close" }, cleanup = true) {
                dataStore.close()
            }
        }
    }

    if (exceptionList.isNotEmpty()) {
        val messages = StringBuilder("DataStore Tests failed: (${exceptionList.size})[\n")
        var firstThrowable: Throwable? = null
        for ((name, exception) in exceptionList) {
            if (firstThrowable == null) {
                firstThrowable = exception
            }
            messages.append("\t$name: $exception\n")
        }
        messages.append(']')
        throw RuntimeException(messages.toString(), firstThrowable)
    }
}

private suspend fun runBoundedCasePhase(
    caseTimeout: Duration,
    exceptionList: MutableMap<String, Throwable>,
    testClassName: String,
    testName: String,
    phase: () -> String,
    cleanup: Boolean = false,
    action: suspend () -> Unit,
) {
    try {
        if (cleanup) {
            withContext(NonCancellable) {
                runWithCaseTimeout(caseTimeout, action)
            }
        } else {
            runWithCaseTimeout(caseTimeout, action)
        }
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) {
            throw throwable
        }
        val failedPhase = phase()
        println("  FAILED $failedPhase")
        exceptionList["$testClassName.$testName.$failedPhase"] = throwable
        throwable.printStackTrace()
    }
}

private suspend fun runWithCaseTimeout(
    caseTimeout: Duration,
    action: suspend () -> Unit,
) = withContext(testCaseDispatcher) {
    if (withTimeoutOrNull(caseTimeout) {
            action()
            true
        } != true
    ) {
        throw DataStoreTestTimeoutException(caseTimeout)
    }
}

private class DataStoreTestTimeoutException(caseTimeout: Duration) :
    RuntimeException("Datastore test phase timed out after $caseTimeout.")
