package maryk.datastore.test

import maryk.datastore.shared.IsDataStore

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
        var messages = "DataStore Tests failed: [\n"
        for ((name, exception) in exceptionList) {
            messages += "\t$name: $exception\n"
        }
        throw Exception("$messages]")
    }
}
