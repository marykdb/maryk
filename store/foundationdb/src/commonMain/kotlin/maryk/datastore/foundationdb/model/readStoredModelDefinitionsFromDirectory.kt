package maryk.datastore.foundationdb.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import maryk.core.models.RootDataModel
import maryk.core.models.migration.StorageFormat
import maryk.datastore.foundationdb.openFoundationDatabase
import maryk.datastore.shared.StoredModelDefinitionsContext
import maryk.datastore.shared.loadStoredModelDefinitions
import maryk.datastore.foundationdb.metadata.readStoredModelNames
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.migration.decodeStorageFormat
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.runSuspend
import maryk.foundationdb.directory.DirectoryLayer
import kotlinx.coroutines.withTimeout
import maryk.foundationdb.directory.DirectorySubspace
import maryk.foundationdb.directory.asDirectory
import kotlin.time.Duration.Companion.milliseconds

private val storeMetadataModelsByIdDirectoryPath = listOf("__meta__", "models_by_id")

/**
 * Open an existing FoundationDB directory tree and read all stored model definitions by id.
 * Returns an id -> RootDataModel map. If the root or metadata directory is missing, returns an empty map.
 */
suspend fun readStoredModelDefinitionsFromDirectory(
    fdbClusterFilePath: String? = null,
    directoryPath: List<String> = listOf("maryk"),
): Map<UInt, RootDataModel<*>> {
    val db = openFoundationDatabase(fdbClusterFilePath)

    val tc: TransactionContext = db

    try {
        return withContext(Dispatchers.IO) {
            val rootDirectory: DirectorySubspace = try {
                withTimeout(10_000.milliseconds) {
                    tc.runSuspend { tr ->
                        DirectoryLayer.getDefault().open(tr, directoryPath).await()
                    }
                }
            } catch (e: Throwable) {
                if (e.isMissingDirectory()) return@withContext emptyMap()
                throw e
            }

            val metadataDirectory: DirectorySubspace = try {
                withTimeout(10_000.milliseconds) {
                    tc.runSuspend { tr ->
                        rootDirectory.open(tr, storeMetadataModelsByIdDirectoryPath).await()
                    }
                }
            } catch (e: Throwable) {
                if (e.isMissingDirectory()) return@withContext emptyMap()
                throw e
            }
            val metadataPrefix = metadataDirectory.pack()
            val legacyStorage = withTimeout(10_000.milliseconds) {
                tc.runSuspend { tr ->
                    val path = listOf("__meta__", "storage_format")
                    val formatDirectory = if (rootDirectory.asDirectory().exists(tr, path).await()) {
                        rootDirectory.open(tr, path).await()
                    } else null
                    val format = formatDirectory?.let { tr.get(it.pack() + StorageFormatMetadataKey).await() }
                    decodeStorageFormat(format) != StorageFormat.CURRENT
                }
            }

            val storedNamesById = withTimeout(10_000.milliseconds) {
                readStoredModelNames(tc, metadataPrefix)
            }
            if (storedNamesById.isEmpty()) {
                return@withContext emptyMap()
            }

            val conversionContext = StoredModelDefinitionsContext()
            val storedModelsByEntry = loadStoredModelDefinitions(storedNamesById.entries) { (_, modelName) ->
                val modelPrefix = withTimeout(10_000.milliseconds) {
                    tc.runSuspend { tr ->
                        rootDirectory.open(tr, listOf(modelName, "meta")).await().pack()
                    }
                }

                readStoredModelDefinition(tc, modelPrefix, conversionContext, legacyStorage)
            }
            storedModelsByEntry.mapKeys { (entry, _) -> entry.key }
        }
    } finally {
        db.close()
    }
}

private fun Throwable.isMissingDirectory(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current::class.simpleName == "NoSuchDirectoryException") return true
        current = current.cause
    }
    return false
}
