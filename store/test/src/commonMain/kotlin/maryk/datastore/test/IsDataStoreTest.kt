package maryk.datastore.test

/** Defines a test for a DataStore */
interface IsDataStoreTest {
    val allTests: Map<String, suspend () -> Any>

    /** Add data needed for test to store */
    suspend fun initData() {}

    /** Command to run after each test to reset data */
    suspend fun resetData()
}
