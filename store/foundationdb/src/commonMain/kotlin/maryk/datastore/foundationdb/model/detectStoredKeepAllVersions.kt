package maryk.datastore.foundationdb.model

import kotlinx.coroutines.withTimeout
import maryk.datastore.foundationdb.openFoundationDatabase
import maryk.datastore.shared.rethrowIfFatal
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectorySubspace
import maryk.foundationdb.runSuspend
import kotlin.time.Duration.Companion.milliseconds

/**
 * Probe the first stored model's history directory without opening or modifying a Maryk store.
 * Returns null when no model is supplied or discovery cannot determine the setting.
 */
suspend fun detectStoredKeepAllVersions(
    clusterFilePath: String?,
    directoryPath: List<String>,
    modelNames: List<String>,
): Boolean? {
    val modelName = modelNames.firstOrNull() ?: return null
    val db = openFoundationDatabase(clusterFilePath)

    val tc: TransactionContext = db

    try {
        val rootDirectory: DirectorySubspace = try {
            withTimeout(10_000.milliseconds) {
                tc.runSuspend { tr ->
                    DirectoryLayer.getDefault().open(tr, directoryPath).await()
                }
            }
        } catch (e: Throwable) {
            e.rethrowIfFatal()
            return null
        }

        return try {
            withTimeout(10_000.milliseconds) {
                tc.runSuspend { tr ->
                    rootDirectory.open(tr, listOf(modelName, "table_versioned")).await()
                }
            }
            true
        } catch (t: Throwable) {
            t.rethrowIfFatal()
            if (t.isNoSuchDirectory()) false else null
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
