package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.CollectionFilterEncryptionProvider
import maryk.datastore.test.CollectionPresenceRecord
import maryk.datastore.test.assertComplexCollectionComparisons
import maryk.datastore.test.assertEmptyCollectionPresence
import kotlin.test.Test
import kotlin.random.Random

class EmptyCollectionPresenceTest {
    @Test
    fun exactExistsDistinguishesAbsentEmptyNonemptyAndDeletedContainers() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("empty-collection-presence-${Random.nextInt()}", mapOf(1u to CollectionPresenceRecord), keepAllVersions = true, fieldEncryptionProvider = CollectionFilterEncryptionProvider())
        try {
            assertEmptyCollectionPresence(store)
        } finally {
            store.close()
        }
    }
    @Test
    fun exactComplexComparisonsDecodeTheirSelectedStructure() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("complex-collection-comparisons-${Random.nextInt()}", mapOf(1u to CollectionPresenceRecord), keepAllVersions = true, fieldEncryptionProvider = CollectionFilterEncryptionProvider())
        try {
            assertComplexCollectionComparisons(store)
        } finally {
            store.close()
        }
    }
}
