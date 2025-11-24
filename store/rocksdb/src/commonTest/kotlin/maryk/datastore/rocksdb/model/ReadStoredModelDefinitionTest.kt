package maryk.datastore.rocksdb.model

import kotlinx.coroutines.test.runTest
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsConversionContext
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.metadata.ModelMeta
import maryk.datastore.rocksdb.metadata.readMetaFile
import maryk.deleteFolder
import maryk.test.models.ModelWithDependents
import maryk.test.models.SimpleMarykModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReadStoredModelDefinitionTest {
    private lateinit var dbPath: String

    @BeforeTest
    fun setUp() {
        dbPath = createTestDBFolder("read-model-definition")
    }

    @AfterTest
    fun tearDown() {
        deleteFolder(dbPath)
    }

    @Test
    fun readsStoredModelDefinitionAndDependentsIntoContext() = runTest {
        val dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = dbPath,
            dataModelsById = mapOf(1u to ModelWithDependents)
        )

        val conversionContext = DefinitionsConversionContext()
        val columnFamilies = dataStore.getColumnFamilies(1u)

        try {
            val storedDataModel = readStoredModelDefinition(dataStore.db, columnFamilies.model, conversionContext)

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
    fun readsStoredModelDefinitionsFromPath() = runTest {
        val dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = dbPath,
            dataModelsById = mapOf(
                1u to ModelWithDependents,
                2u to SimpleMarykModel,
            )
        )

        // Ensure meta file is written with model info
        val storedNames = dataStore.readStoredModelNamesById()
        assertFalse(storedNames.isEmpty())

        dataStore.close()

        val metas = readMetaFile(dbPath)
        assertEquals(
            mapOf(
                1u to ModelMeta(ModelWithDependents.Meta.name, ModelWithDependents.Meta.keyByteSize),
                2u to ModelMeta(SimpleMarykModel.Meta.name, SimpleMarykModel.Meta.keyByteSize),
            ),
            metas
        )

        val storedModels = readStoredModelDefinitionsFromPath(dbPath)

        assertEquals(setOf(1u, 2u), storedModels.keys)
        assertEquals(ModelWithDependents.Meta.name, storedModels[1u]?.Meta?.name)
        assertEquals(SimpleMarykModel.Meta.name, storedModels[2u]?.Meta?.name)
    }
}
