package maryk.datastore.foundationdb.model

import maryk.core.models.RootDataModel
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.foundationdb.metadata.readStoredModelNames
import maryk.foundationdb.FDB
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.runSuspend
import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.tuple.Tuple
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
    tenantName: Tuple? = null,
): Map<UInt, RootDataModel<*>> {
    val fdb = FDB.selectAPIVersion(730)
    val db = if (fdbClusterFilePath != null) fdb.open(fdbClusterFilePath) else fdb.open()

    val tenantDb = tenantName?.let { db.openTenant(it) }
    val tc: TransactionContext = tenantDb ?: db

    try {
        val rootDirectory: DirectorySubspace = withTimeout(10_000) {
            tc.runSuspend { tr ->
                DirectoryLayer.getDefault().open(tr, directoryPath).await()
            }
        }

        val metadataDirectory: DirectorySubspace = withTimeout(10_000) {
            tc.runSuspend { tr ->
                rootDirectory.open(tr, storeMetadataModelsByIdDirectoryPath).await()
            }
        }
        val metadataPrefix = metadataDirectory.pack()

        val storedNamesById = withTimeout(10_000) {
            readStoredModelNames(tc, metadataPrefix)
        }
        if (storedNamesById.isEmpty()) {
            return emptyMap()
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

        return storedModelsById
    } finally {
        tenantDb?.close()
        db.close()
    }
}
