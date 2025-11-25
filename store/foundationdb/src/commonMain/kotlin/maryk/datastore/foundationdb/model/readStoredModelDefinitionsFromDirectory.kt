package maryk.datastore.foundationdb.model

import maryk.core.models.RootDataModel
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.foundationdb.metadata.readStoredModelNames
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.foundationdb.FDB
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.tuple.Tuple

private val storeMetadataModelsByIdDirectoryPath = listOf("__meta__", "models_by_id")

/**
 * Open an existing FoundationDB directory tree and read all stored model definitions by id.
 * Returns an id -> RootDataModel map. If the root or metadata directory is missing, returns an empty map.
 */
fun readStoredModelDefinitionsFromDirectory(
    fdbClusterFilePath: String? = null,
    directoryPath: List<String> = listOf("maryk"),
    tenantName: Tuple? = null,
): Map<UInt, RootDataModel<*>> {
    val fdb = FDB.selectAPIVersion(730)
    val db = if (fdbClusterFilePath != null) fdb.open(fdbClusterFilePath) else fdb.open()
    val tenantDb = tenantName?.let { db.openTenant(it) }
    val tc: TransactionContext = tenantDb ?: db

    try {
        val rootDirectory = try {
            tc.run { tr ->
                DirectoryLayer.getDefault().createOrOpen(tr, directoryPath).awaitResult()
            }
        } catch (_: Exception) {
            // Directory tree not present and cannot be created; nothing to read
            return emptyMap()
        }

        val metadataDirectory = try {
            tc.run { tr ->
                rootDirectory.createOrOpen(tr, storeMetadataModelsByIdDirectoryPath).awaitResult()
            }
        } catch (_: Exception) {
            return emptyMap()
        }
        val metadataPrefix = metadataDirectory.pack()

        val storedNamesById = readStoredModelNames(tc, metadataPrefix)
        if (storedNamesById.isEmpty()) return emptyMap()

        val conversionContext = DefinitionsConversionContext()
        val storedModelsById = mutableMapOf<UInt, RootDataModel<*>>()

        for ((id, modelName) in storedNamesById) {
            val modelPrefix = try {
                tc.run { tr ->
                    rootDirectory.createOrOpen(tr, listOf(modelName, "meta")).awaitResult().pack()
                }
            } catch (_: Exception) {
                continue
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
