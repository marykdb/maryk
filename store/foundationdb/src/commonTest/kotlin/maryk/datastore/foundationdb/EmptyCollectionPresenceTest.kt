package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.CollectionFilterEncryptionProvider
import maryk.datastore.test.assertCollectionFilterEncryptionIsolation
import maryk.datastore.test.CollectionPresenceRecord
import maryk.datastore.test.assertComplexCollectionComparisons
import maryk.datastore.test.assertEmptyCollectionPresence
import kotlin.test.Test
import kotlin.random.Random

class EmptyCollectionPresenceTest {
    @Test
    fun exactExistsDistinguishesAbsentEmptyNonemptyAndDeletedContainers() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", "empty-collection-presence-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to CollectionPresenceRecord), keepAllVersions = true, fieldEncryptionProvider = CollectionFilterEncryptionProvider())
        try {
            assertEmptyCollectionPresence(store)
        } finally {
            store.close()
        }
    }
    @Test
    fun exactComplexComparisonsDecodeTheirSelectedStructure() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", "complex-collection-comparisons-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to CollectionPresenceRecord), keepAllVersions = true, fieldEncryptionProvider = CollectionFilterEncryptionProvider())
        try {
            assertComplexCollectionComparisons(store)
        } finally {
            store.close()
        }
    }
    @Test
    fun presenceAndSelectedComparisonsDoNotDecryptUnselectedSecrets() = runTest {
        val provider = CollectionFilterEncryptionProvider()
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", "collection-filter-encryption-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to CollectionPresenceRecord), keepAllVersions = true, fieldEncryptionProvider = provider)
        try {
            assertCollectionFilterEncryptionIsolation(store, provider)
        } finally {
            store.close()
        }
    }

}
