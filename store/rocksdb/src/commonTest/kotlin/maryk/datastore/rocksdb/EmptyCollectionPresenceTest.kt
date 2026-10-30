package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.CollectionFilterEncryptionProvider
import maryk.datastore.test.assertCollectionFilterEncryptionIsolation
import maryk.datastore.test.CollectionPresenceRecord
import maryk.datastore.test.assertComplexCollectionComparisons
import maryk.datastore.test.assertEmptyCollectionPresence
import kotlin.test.Test
import maryk.createTestDBFolder
import maryk.deleteFolder

class EmptyCollectionPresenceTest {
    @Test
    fun exactExistsDistinguishesAbsentEmptyNonemptyAndDeletedContainers() = runTest {
        val folder = createTestDBFolder("empty-collection-presence")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to CollectionPresenceRecord), keepAllVersions = true, fieldEncryptionProvider = CollectionFilterEncryptionProvider())
        try {
            assertEmptyCollectionPresence(store)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
    @Test
    fun exactComplexComparisonsDecodeTheirSelectedStructure() = runTest {
        val folder = createTestDBFolder("complex-collection-comparisons")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to CollectionPresenceRecord), keepAllVersions = true, fieldEncryptionProvider = CollectionFilterEncryptionProvider())
        try {
            assertComplexCollectionComparisons(store)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
    @Test
    fun presenceAndSelectedComparisonsDoNotDecryptUnselectedSecrets() = runTest {
        val folder = createTestDBFolder("collection-filter-encryption")
        val provider = CollectionFilterEncryptionProvider()
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to CollectionPresenceRecord), keepAllVersions = true, fieldEncryptionProvider = provider)
        try {
            assertCollectionFilterEncryptionIsolation(store, provider)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

}
