package maryk.datastore.shared.migration

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.UInt32
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object CollectionInventoryModel : RootDataModel<CollectionInventoryModel>() {
    val list by list(1u, valueDefinition = StringDefinition())
    val set by set(2u, valueDefinition = StringDefinition())
    val map by map(3u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
    val allocated by incrementingMap(4u, keyNumberDescriptor = UInt32,
        valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
    val label by string(5u)
}

private object RecursiveNoCollection : DataModel<RecursiveNoCollection>() {
    val next by embed(1u, dataModel = { RecursiveNoCollection }, required = false)
}

private object EmbeddedNoCollection : RootDataModel<EmbeddedNoCollection>() {
    val child by embed(1u, dataModel = { RecursiveNoCollection })
}

class LegacyCollectionStorageTest {
    @Test
    fun classifiesCollectionCountsAndNestedAllocatedCountsWithoutConfusingScalarTombstones() {
        assertTrue(CollectionInventoryModel.hasLegacyCollectionStorage())
        assertTrue(CollectionInventoryModel.hasLegacyIncrementingMapStorage())
        assertFalse(EmbeddedNoCollection.hasLegacyCollectionStorage())
        assertFalse(EmbeddedNoCollection.hasLegacyIncrementingMapStorage())
        for (qualifier in listOf(
            CollectionInventoryModel.list.ref().toStorageByteArray(),
            CollectionInventoryModel.set.ref().toStorageByteArray(),
            CollectionInventoryModel.map.ref().toStorageByteArray(),
            CollectionInventoryModel.allocated.ref().toStorageByteArray(),
            CollectionInventoryModel.ref { allocated at 1u }.toStorageByteArray(),
        )) {
            assertTrue(CollectionInventoryModel.isLegacyZeroCollectionCount(qualifier, byteArrayOf(0)))
            assertFalse(CollectionInventoryModel.isLegacyZeroCollectionCount(qualifier, byteArrayOf(0, 0)))
            assertFalse(CollectionInventoryModel.isLegacyZeroCollectionCount(qualifier, byteArrayOf(1)))
        }
        assertFalse(CollectionInventoryModel.isLegacyZeroCollectionCount(CollectionInventoryModel.label.ref().toStorageByteArray(), byteArrayOf(0)))
    }
}
