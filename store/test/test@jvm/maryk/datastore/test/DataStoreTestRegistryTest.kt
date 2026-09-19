package maryk.datastore.test

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStoreTestRegistryTest {
    @Test
    fun registryIncludesEverySharedDataStoreTestClass() {
        val commonMainDirectory = File(requireNotNull(System.getProperty("maryk.store.test.commonMain")))
        val registrySource = File(commonMainDirectory, "runDataStoreTests.kt").readText()
        val testSources = commonMainDirectory.walkTopDown()
            .filter { it.extension == "kt" && it.name != "runDataStoreTests.kt" }
            .toList()

        assertEquals(emptySet(), findUnregisteredDataStoreTestClasses(testSources, registrySource))
    }

    @Test
    fun identifiesAnUnregisteredSharedDataStoreTestClass() {
        val source = File.createTempFile("UnregisteredDataStoreTest", ".kt")
        try {
            source.writeText(
                """
                class UnregisteredDataStoreTest(
                    val dataStore: IsDataStore,
                ) : IsDataStoreTest {
                    override val allTests = emptyMap<String, suspend () -> Any>()
                    override suspend fun resetData() = Unit
                }
                """.trimIndent()
            )

            assertEquals(
                setOf("UnregisteredDataStoreTest"),
                findUnregisteredDataStoreTestClasses(listOf(source), "private val allTestClasses = arrayOf()"),
            )
        } finally {
            source.delete()
        }
    }
}
