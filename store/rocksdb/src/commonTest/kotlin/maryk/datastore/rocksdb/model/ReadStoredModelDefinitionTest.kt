package maryk.datastore.rocksdb.model

import kotlinx.coroutines.test.runTest
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsConversionContext
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.deleteFolder
import maryk.test.models.ModelWithDependents
import maryk.test.models.SimpleMarykModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun returnsNullWhenNoStoredDefinition() = runTest {
        val dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = dbPath,
            dataModelsById = mapOf(1u to ModelWithDependents)
        )

        val columnFamilies = dataStore.getColumnFamilies(1u)
        val conversionContext = DefinitionsConversionContext()

        dataStore.db.delete(columnFamilies.model, modelDefinitionKey)
        dataStore.db.delete(columnFamilies.model, modelDependentsDefinitionKey)

        try {
            val storedDataModel = readStoredModelDefinition(dataStore.db, columnFamilies.model, conversionContext)
            assertNull(storedDataModel)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun readsAllStoredModelDefinitionsById() = runTest {
        val dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = dbPath,
            dataModelsById = mapOf(
                1u to ModelWithDependents,
                2u to SimpleMarykModel,
            )
        )

        try {
            val storedNames = dataStore.readStoredModelNamesById()
            assertEquals(
                mapOf(
                    1u to ModelWithDependents.Meta.name,
                    2u to SimpleMarykModel.Meta.name,
                ),
                storedNames
            )

            val storedModels = readStoredModelDefinitionsById(dataStore)

            assertEquals(setOf(1u, 2u), storedModels.keys)

            val storedModelOne = storedModels[1u]
            assertNotNull(storedModelOne)
            assertEquals(ModelWithDependents.Meta.name, storedModelOne.Meta.name)
            assertEquals(ModelWithDependents.Meta.version, storedModelOne.Meta.version)

            val storedModelTwo = storedModels[2u]
            assertNotNull(storedModelTwo)
            assertEquals(SimpleMarykModel.Meta.name, storedModelTwo.Meta.name)
            assertEquals(SimpleMarykModel.Meta.version, storedModelTwo.Meta.version)
        } finally {
            dataStore.close()
        }
    }

    @Test
    fun skipsMissingModelDefinitionsWhenReadingAll() = runTest {
        val dataStore = RocksDBDataStore.open(
            keepAllVersions = true,
            relativePath = dbPath,
            dataModelsById = mapOf(
                1u to ModelWithDependents,
                2u to SimpleMarykModel,
            )
        )

        val modelColumnFamilies2 = dataStore.getColumnFamilies(2u)

        dataStore.db.delete(modelColumnFamilies2.model, modelDefinitionKey)
        dataStore.db.delete(modelColumnFamilies2.model, modelDependentsDefinitionKey)

        try {
            val storedModels = readStoredModelDefinitionsById(dataStore)

            assertEquals(setOf(1u), storedModels.keys)
            val storedModel = storedModels[1u]
            assertNotNull(storedModel)
            assertEquals(ModelWithDependents.Meta.name, storedModel.Meta.name)
        } finally {
            dataStore.close()
        }
    }
}
