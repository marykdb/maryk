@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package maryk.datastore.foundationdb.model

import kotlinx.coroutines.test.runTest
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.test.models.ModelWithDependents
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ReadStoredModelDefinitionTest {
    @Test
    fun readsStoredModelDefinitionAndDependentsIntoContext() = runTest {
        val dirPath = listOf("maryk", "test", "fdb-read-model", Uuid.random().toString())
        val dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelWithDependents)
        )

        val conversionContext = DefinitionsConversionContext()
        val modelPrefix = dataStore.getTableDirs(1u).modelPrefix

        try {
            val storedDataModel = readStoredModelDefinition(dataStore.tc, modelPrefix, conversionContext)

            assertNotNull(storedDataModel)
            assertEquals(ModelWithDependents.Meta.name, storedDataModel.Meta.name)
            assertEquals(ModelWithDependents.Meta.version, storedDataModel.Meta.version)

            val reference = conversionContext.dataModels[ModelWithDependents.Meta.name] as? DataModelReference
            assertNotNull(reference)
            assertEquals(storedDataModel, reference.get())

            // Ensure dependent model definitions were hydrated into the context
            assertTrue(conversionContext.dataModels.keys.any { it != ModelWithDependents.Meta.name })
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun returnsNullWhenNoStoredDefinition() = runTest {
        val dirPath = listOf("maryk", "test", "fdb-read-model-missing", Uuid.random().toString())
        val dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(1u to ModelWithDependents)
        )

        val modelPrefix = dataStore.getTableDirs(1u).modelPrefix

        dataStore.tc.run { tr ->
            tr.clear(packKey(modelPrefix, modelDefinitionKey))
            tr.clear(packKey(modelPrefix, modelDependentsDefinitionKey))
        }

        val conversionContext = DefinitionsConversionContext()

        try {
            val storedDataModel = readStoredModelDefinition(dataStore.tc, modelPrefix, conversionContext)
            assertNull(storedDataModel)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun readsStoredModelDefinitionsFromDirectoryWithoutConfiguredMap() = runTest {
        val dirPath = listOf("maryk", "test", "fdb-read-model-from-directory", Uuid.random().toString())
        val dataStore = FoundationDBDataStore.open(
            keepAllVersions = true,
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
            dataModelsById = mapOf(
                1u to ModelWithDependents,
                2u to SimpleMarykModel,
            )
        )

        dataStore.close()

        val storedModels = readStoredModelDefinitionsFromDirectory(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
        )

        assertEquals(setOf(1u, 2u), storedModels.keys)
        assertEquals(ModelWithDependents.Meta.name, storedModels[1u]?.Meta?.name)
        assertEquals(SimpleMarykModel.Meta.name, storedModels[2u]?.Meta?.name)
    }

    @Test
    fun returnsEmptyWhenDirectoryTreeMissing() = runTest {
        val dirPath = listOf("maryk", "test", "fdb-read-model-missing-root", Uuid.random().toString())

        val storedModels = readStoredModelDefinitionsFromDirectory(
            fdbClusterFilePath = "fdb.cluster",
            directoryPath = dirPath,
        )

        assertTrue(storedModels.isEmpty())
    }
}
