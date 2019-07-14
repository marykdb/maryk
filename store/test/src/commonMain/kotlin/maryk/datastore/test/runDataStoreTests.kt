package maryk.datastore.test

import maryk.datastore.shared.IsDataStore
import maryk.test.models.CompleteMarykModel
import maryk.test.models.ComplexModel
import maryk.test.models.Log
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel

private val allTestClasses = arrayOf(
    "DataStoreAddTest" to ::DataStoreAddTest,
    "DataStoreChangeComplexTest" to ::DataStoreChangeComplexTest,
    "DataStoreChangeTest" to ::DataStoreChangeTest,
    "DataStoreChangeValidationTest" to ::DataStoreChangeValidationTest,
    "DataStoreDeleteTest" to ::DataStoreDeleteTest,
    "DataStoreGetChangesComplexTest" to ::DataStoreGetChangesComplexTest,
    "DataStoreGetChangesTest" to ::DataStoreGetChangesTest,
    "DataStoreGetTest" to ::DataStoreGetTest,
    "DataStoreScanChangesTest" to ::DataStoreScanChangesTest,
    "DataStoreScanOnIndexTest" to ::DataStoreScanOnIndexTest,
    "DataStoreScanTest" to ::DataStoreScanTest,
    "DataStoreScanUniqueTest" to ::DataStoreScanUniqueTest,
    "UniqueTest" to ::UniqueTest
)

val dataModelsForTests = mapOf(
    1u to TestMarykModel,
    2u to SimpleMarykModel,
    3u to ComplexModel,
    4u to Log,
    5u to CompleteMarykModel,
    6u to UniqueModel
)

fun runDataStoreTests(dataStore: IsDataStore, runOnlyTest: String? = null) {
    val exceptionList = mutableMapOf<String, Throwable>()

    for ((testClassName, testClassConstructor) in allTestClasses) {
        val testClass = testClassConstructor(dataStore)

        try {
            for ((testName, test) in testClass.allTests) {
                if (runOnlyTest != null && testName != runOnlyTest) {
                    continue
                }

                testClass.initData()

                try {
                    test()
                } catch (throwable: Throwable) {
                    exceptionList["$testClassName.$testName"] = throwable
                }

                testClass.resetData()
            }
        } catch (throwable: Throwable) {
            exceptionList["$testClassName:initData"] = throwable
        }
    }
    if (exceptionList.isNotEmpty()) {
        var messages = "DataStore Tests failed: (${exceptionList.size})[\n"
        for ((name, exception) in exceptionList) {
            messages += "\t$name: $exception\n"
        }
        throw AssertionError("$messages]")
    }
}