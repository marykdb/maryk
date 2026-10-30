package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.models.DataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.definitions.DataModelDefinition
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

private object FirstRecursiveDetail : DataModel<FirstRecursiveDetail>({ DataModelDefinition("RetainedRecursiveDetail") }) {
    val value by number(1u, type = Float64)
    val next by embed(2u, dataModel = { FirstRecursiveDetail }, required = false)
}

private object SecondRecursiveDetail : DataModel<SecondRecursiveDetail>({ DataModelDefinition("RetainedRecursiveDetail") }) {
    val value by number(1u, type = UInt32)
    val next by embed(2u, dataModel = { SecondRecursiveDetail }, required = false)
}

private object FirstRecursiveSource : RootDataModel<FirstRecursiveSource>() {
    val detail by embed(1u, dataModel = { FirstRecursiveDetail })
}

private object SecondRecursiveSource : RootDataModel<SecondRecursiveSource>() {
    val detail by embed(1u, dataModel = { SecondRecursiveDetail })
}

class SourceSchemaStorageFormatUpgradeTest {
    @Test
    fun retainedSourcePairsKeepTheirOwnRecursiveDependenciesInEitherModelOrder() = runTest {
        installIndexedDbForTests()
        for (reverse in listOf(false, true)) {
            val sources: List<IsRootDataModel> = listOf(FirstRecursiveSource, SecondRecursiveSource).let { if (reverse) it.reversed() else it }
            val database = "maryk-isolated-recursive-schemas-$reverse-${Random.nextInt()}"
            suspend fun open(id: UInt, source: IsRootDataModel) = IndexedDbDataStore.open(database, mapOf(id to source))
            suspend fun assertSources(bytes: IndexedDbByteStore) {
                val context = DefinitionsConversionContext()
                val models = (1u..2u).associateWith { id -> assertNotNull(bytes.readStoredModelDefinition(id, context)) }
                for (source in models.values) {
                    val expectedType = if (source.Meta.name == FirstRecursiveSource.Meta.name) Float64 else UInt32
                    val detail = assertIs<IsEmbeddedValuesDefinition<*, *>>(source[1u]?.definition).dataModel
                    val recursive = assertIs<IsEmbeddedValuesDefinition<*, *>>(detail[2u]?.definition).dataModel
                    assertEquals(expectedType, assertIs<NumberDefinition<*>>(detail[1u]?.definition).type)
                    assertEquals(expectedType, assertIs<NumberDefinition<*>>(recursive[1u]?.definition).type)
                }
            }
            var store = open(1u, sources[0])
            try {
                store.close()
                store = open(2u, sources[1])
                store.byteStore.delete("meta", StorageFormatMetadataKey)
                assertSources(store.byteStore)
                store.close()
                repeat(2) {
                    store = open(3u, SimpleMarykModel)
                    assertSources(store.byteStore)
                    store.close()
                }
            } finally {
                store.close()
            }
        }
    }
}
