package io.maryk.app.config

import kotlinx.coroutines.runBlocking
import maryk.core.models.RootDataModel
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.model.detectStoredKeepAllVersions
import maryk.datastore.foundationdb.model.readStoredModelDefinitionsFromDirectory
import maryk.datastore.remote.RemoteDataStore
import maryk.datastore.remote.RemoteSshConfig
import maryk.datastore.remote.RemoteStoreConfig
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.model.readStoredModelDefinitionsFromPath
import maryk.rocksdb.DBOptions
import maryk.rocksdb.Options
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.recoverNonFatal
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.runCatchingNonFatal
import maryk.file.File

sealed class ConnectResult {
    data class Success(val connection: StoreConnection) : ConnectResult()
    data class Error(val message: String) : ConnectResult()
}

data class StoreConnection(
    val definition: StoreDefinition,
    val dataStore: IsDataStore,
) {
    fun close() {
        runBlocking {
            dataStore.closeConnectionResources()
        }
    }
}

private suspend fun IsDataStore.closeConnectionResources() {
    val listenerFailure = runCatchingNonFatal { closeAllListeners() }.exceptionOrNull()
    val storeFailure = runCatchingNonFatal { close() }.exceptionOrNull()
    when {
        listenerFailure != null -> {
            storeFailure?.let(listenerFailure::addSuppressed)
            throw listenerFailure
        }
        storeFailure != null -> throw storeFailure
    }
}

class StoreConnector {
    fun connect(definition: StoreDefinition): ConnectResult = when (definition.type) {
        StoreKind.ROCKS_DB -> connectRocksDb(definition)
        StoreKind.FOUNDATION_DB -> connectFoundationDb(definition)
        StoreKind.REMOTE -> connectRemote(definition)
    }

    private fun connectRocksDb(definition: StoreDefinition): ConnectResult {
        return recoverNonFatal(
            block = {
                val modelsById = loadStoredModels(definition.directory)
                val dataStore = runBlocking {
                    RocksDBDataStore.open(
                        relativePath = definition.directory,
                        dataModelsById = modelsById,
                    )
                }
                ConnectResult.Success(
                    StoreConnection(
                        definition = definition,
                        dataStore = dataStore,
                    )
                )
            },
            recover = { e ->
                val reason = if (e.isRocksDbLockError()) {
                    "RocksDB already open in another process. Close it or open a copy of the store directory."
                } else {
                    e.message ?: e::class.simpleName ?: "Unknown error"
                }
                ConnectResult.Error(reason)
            }
        )
    }

    private fun loadStoredModels(path: String): Map<UInt, RootDataModel<*>> =
        Options().use { listOptions ->
            DBOptions().use { dbOptions ->
                readStoredModelDefinitionsFromPath(path, listOptions, dbOptions)
            }
        }

    private fun connectFoundationDb(definition: StoreDefinition): ConnectResult {
        return try {
            val directoryPath = definition.directory.split('/')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (directoryPath.isEmpty()) {
                return ConnectResult.Error("FoundationDB directory path is required.")
            }

            val modelsById = runBlocking {
                readStoredModelDefinitionsFromDirectory(
                    fdbClusterFilePath = definition.clusterFile,
                    directoryPath = directoryPath,
                )
            }

            val effectiveModels = modelsById.ifEmpty {
                val dir = directoryPath.joinToString("/")
                val cluster = definition.clusterFile ?: "default cluster"
                return ConnectResult.Error(
                    "No stored models found at directory `$dir` on $cluster. Check the directory path or seed data first."
                )
            }

            val keepAllVersionsPreference = runBlocking {
                detectStoredKeepAllVersions(
                    clusterFilePath = definition.clusterFile,
                    directoryPath = directoryPath,
                    modelNames = effectiveModels.values.map { it.Meta.name },
                )
            } ?: true

            val dataStore = runBlocking {
                FoundationDBDataStore.open(
                    keepAllVersions = keepAllVersionsPreference,
                    fdbClusterFilePath = definition.clusterFile,
                    directoryPath = directoryPath,
                    dataModelsById = effectiveModels,
                )
            }

            ConnectResult.Success(
                StoreConnection(
                    definition = definition,
                    dataStore = dataStore,
                )
            )
        } catch (t: Throwable) {
            val looksLikeMissingLib = t::class.simpleName == "UnsatisfiedLinkError" ||
                (t.message?.contains("fdb_c", ignoreCase = true) == true)
            val reason = if (looksLikeMissingLib) {
                "FoundationDB native client (libfdb_c) is missing. Install via store/foundationdb/scripts/install-foundationdb.sh or install system-wide."
            } else {
                t.rethrowIfFatal()
                t.message ?: t::class.simpleName ?: "Unknown error"
            }
            ConnectResult.Error(reason)
        }
    }

    private fun connectRemote(definition: StoreDefinition): ConnectResult {
        val url = definition.directory.trim()
        if (url.isBlank()) {
            return ConnectResult.Error("Remote URL is required.")
        }

        val sshConfig = definition.sshHost?.takeIf { it.isNotBlank() }?.let { host ->
            RemoteSshConfig(
                host = host,
                user = definition.sshUser?.takeIf { it.isNotBlank() },
                port = definition.sshPort ?: 22,
                localPort = definition.sshLocalPort,
                identityFile = definition.sshIdentityFile?.takeIf { it.isNotBlank() },
            )
        }

        return recoverNonFatal(
            block = {
                val bearerToken = definition.bearerTokenFile?.let(::readBearerToken)
                val dataStore = runBlocking {
                    RemoteDataStore.connect(
                        RemoteStoreConfig(
                            baseUrl = url,
                            ssh = sshConfig,
                            bearerToken = bearerToken,
                        )
                    )
                }
                ConnectResult.Success(
                    StoreConnection(
                        definition = definition,
                        dataStore = dataStore,
                    )
                )
            },
            recover = { e ->
                ConnectResult.Error(e.message ?: e::class.simpleName ?: "Unknown error")
            }
        )
    }
}

internal fun readBearerToken(path: String): String {
    val token = File.readText(path, MAX_BEARER_TOKEN_BYTES) ?: run {
        if (File.size(path) != null) {
            throw IllegalArgumentException("Bearer token file exceeds max size: $MAX_BEARER_TOKEN_BYTES bytes")
        }
        throw IllegalArgumentException("Bearer token file not found: $path")
    }
    return token.trimEnd('\r', '\n').also {
        require(it.isNotBlank()) { "Bearer token file cannot be blank." }
    }
}

private const val MAX_BEARER_TOKEN_BYTES = 16 * 1024

private fun Throwable.isRocksDbLockError(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        val message = current.message?.lowercase() ?: ""
        if (message.contains("lock file") || (message.contains("lock") && message.contains("resource temporarily unavailable"))) {
            return true
        }
        current = current.cause
    }
    return false
}
