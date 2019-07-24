package maryk.datastore.test

/** Defines a test for a DataStore */
interface IsDataStoreTest {
    val allTests: Map<String, () -> Any>

    /** Add data needed for test to store */
    fun initData() {}

    /** Command to run after each test to reset data */
    fun resetData()
}
