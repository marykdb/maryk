package maryk.datastore.terminal.driver

import maryk.core.models.RootDataModel
import maryk.core.properties.types.Version

/**
 * Enum describing the supported backing stores that the terminal client can connect to.
 */
enum class StoreType(val displayName: String) {
    RocksDb("RocksDB"),
    FoundationDb("FoundationDB"),
}

/**
 * Description of a stored model as retrieved from the data store metadata.
 */
data class StoredModel(
    val name: String,
    val version: Version,
    val definition: RootDataModel<*>,
)

/**
 * Common contract for store specific drivers that can be queried by the terminal client.
 */
interface StoreDriver : AutoCloseable {
    val type: StoreType

    /** Human readable description of the connection, shown in the UI header. */
    val description: String

    /** Establish the connection to the backing store and prepare metadata access. */
    suspend fun connect()

    /** Retrieve the complete list of stored models. */
    suspend fun listModels(): List<StoredModel>

    /** Load a single model by name. */
    suspend fun loadModel(name: String): StoredModel?
}
