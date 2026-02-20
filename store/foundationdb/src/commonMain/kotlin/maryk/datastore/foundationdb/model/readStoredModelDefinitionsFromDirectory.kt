package maryk.datastore.foundationdb.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import maryk.core.models.RootDataModel
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.foundationdb.metadata.readStoredModelNames
import maryk.foundationdb.FDB
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.runSuspend
import maryk.foundationdb.directory.DirectoryLayer
import kotlinx.coroutines.withTimeout
import maryk.foundationdb.directory.DirectorySubspace

private val storeMetadataModelsByIdDirectoryPath = listOf("__meta__", "models_by_id")

/**
 * Open an existing FoundationDB directory tree and read all stored model definitions by id.
 * Returns an id -> RootDataModel map. If the root or metadata directory is missing, returns an empty map.
 */
suspend fun readStoredModelDefinitionsFromDirectory(
    fdbClusterFilePath: String? = null,
    directoryPath: List<String> = listOf("maryk"),
): Map<UInt, RootDataModel<*>> {
    val fdb = FDB.selectAPIVersion(730)
    val db = if (fdbClusterFilePath != null) fdb.open(fdbClusterFilePath) else fdb.open()

    val tc: TransactionContext = db

    try {
        return withContext(Dispatchers.IO) {
            val rootDirectory: DirectorySubspace = try {
                withTimeout(10_000) {
                    tc.runSuspend { tr ->
                        DirectoryLayer.getDefault().open(tr, directoryPath).await()
                    }
                }
            } catch (e: Throwable) {
                if (e.isNoSuchDirectory()) return@withContext emptyMap()
                throw e
            }

            val metadataDirectory: DirectorySubspace = try {
                withTimeout(10_000) {
                    tc.runSuspend { tr ->
                        rootDirectory.open(tr, storeMetadataModelsByIdDirectoryPath).await()
                    }
                }
            } catch (e: Throwable) {
                if (e.isNoSuchDirectory()) return@withContext emptyMap()
                throw e
            }
            val metadataPrefix = metadataDirectory.pack()

            val storedNamesById = withTimeout(10_000) {
                readStoredModelNames(tc, metadataPrefix)
            }
            if (storedNamesById.isEmpty()) {
                return@withContext emptyMap()
            }

            val conversionContext = DefinitionsConversionContext()
            val storedModelsById = mutableMapOf<UInt, RootDataModel<*>>()

            for ((id, modelName) in storedNamesById) {
                val modelPrefix = withTimeout(10_000) {
                    tc.runSuspend { tr ->
                        rootDirectory.open(tr, listOf(modelName, "meta")).await().pack()
                    }
                }

                val model = readStoredModelDefinition(tc, modelPrefix, conversionContext)
                if (model != null) {
                    storedModelsById[id] = model
                }
            }

            storedModelsById
        }
    } finally {
        db.close()
    }
}

private fun Throwable.isNoSuchDirectory(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current::class.simpleName == "NoSuchDirectoryException") return true
        current = current.cause
    }
    return false
}
