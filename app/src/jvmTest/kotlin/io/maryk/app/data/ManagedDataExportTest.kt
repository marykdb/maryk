package io.maryk.app.data

import kotlinx.coroutines.runBlocking
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt32
import maryk.datastore.memory.InMemoryDataStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ManagedDataExportTest {
    @Test
    fun managedMultiModelExportPublishesCompleteRevisionAndPreservesPriorCurrentOnFailure() = runBlocking {
        val store = InMemoryDataStore.open(
            dataModelsById = mapOf(1u to ManagedExportOne, 2u to ManagedExportTwo),
        )
        val folder = Files.createTempDirectory("maryk-managed-data-export-")
        try {
            exportAllDataToManagedRevision(
                dataStore = store,
                models = listOf(ManagedExportOne, ManagedExportTwo),
                format = DataExportFormat.JSON,
                folder = folder.toString(),
            )

            val currentPath = folder.resolve(".maryk-export/current")
            val firstRevision = Files.readString(currentPath).trim()
            val revisionPath = folder.resolve(".maryk-export/revisions/$firstRevision")
            assertTrue(Files.exists(revisionPath.resolve("${ManagedExportOne.Meta.name}.data.json")))
            assertTrue(Files.exists(revisionPath.resolve("${ManagedExportTwo.Meta.name}.data.json")))

            assertFailsWith<IllegalArgumentException> {
                exportAllDataToManagedRevision(
                    dataStore = store,
                    models = listOf(ManagedExportOne, ManagedExportOne),
                    format = DataExportFormat.JSON,
                    folder = folder.toString(),
                )
            }

            assertEquals(firstRevision, Files.readString(currentPath).trim())
        } finally {
            store.close()
            folder.toFile().deleteRecursively()
        }
    }
}

private object ManagedExportOne : RootDataModel<ManagedExportOne>(
    keyDefinition = { ManagedExportOne.run { id.ref() } },
) {
    val id by number(index = 1u, type = UInt32, final = true)
}

private object ManagedExportTwo : RootDataModel<ManagedExportTwo>(
    keyDefinition = { ManagedExportTwo.run { id.ref() } },
) {
    val id by number(index = 1u, type = UInt32, final = true)
}
