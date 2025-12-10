package io.maryk.cli

import kotlinx.coroutines.runBlocking
import maryk.datastore.shared.IsDataStore

/**
 * Shared mutable state for the CLI session.
 *
 * Currently tracks which store, if any, the user is connected to.
 */
class CliState {
    private var activeConnection: StoreConnection? = null
    private var activeInteraction: ActiveInteraction? = null

    val currentConnection: StoreConnection?
        get() = activeConnection

    val currentDataStore: IsDataStore?
        get() = activeConnection?.dataStore

    val currentInteraction: CliInteraction?
        get() = activeInteraction?.interaction

    /**
     * Replace the current connection with [newConnection].
     *
     * @return The previously active connection, if any.
     */
    fun replaceConnection(newConnection: StoreConnection): StoreConnection? {
        val previous = this.activeConnection
        this.activeConnection = newConnection
        return previous
    }

    /**
     * Clear the current connection.
     *
     * @return The previously active connection, if any.
     */
    fun clearConnection(): StoreConnection? {
        val previous = this.activeConnection
        this.activeConnection = null
        return previous
    }

    fun startInteraction(interaction: CliInteraction) {
        activeInteraction = ActiveInteraction(interaction)
    }

    fun hasActiveInteraction(): Boolean = activeInteraction != null

    fun clearInteraction() {
        activeInteraction = null
    }

    fun interactionIntroShown(): Boolean = activeInteraction?.introShown ?: false

    fun markInteractionIntroShown() {
        activeInteraction?.introShown = true
    }

    fun replaceInteraction(interaction: CliInteraction, showIntro: Boolean) {
        val holder = activeInteraction
        if (holder == null) {
            activeInteraction = ActiveInteraction(interaction, introShown = !showIntro)
        } else {
            holder.interaction = interaction
            holder.introShown = !showIntro
        }
    }

    private class ActiveInteraction(
        var interaction: CliInteraction,
        var introShown: Boolean = false,
    )
}

enum class StoreType(val displayName: String) {
    ROCKS_DB("RocksDB"),
    FOUNDATION_DB("FoundationDB"),
}

/**
 * Represents an active connection to a Maryk store.
 */
interface StoreConnection {
    val type: StoreType
    val dataStore: IsDataStore

    /**
     * Free any resources associated with this connection.
     */
    fun close()
}

data class RocksDbStoreConnection(
    val directory: String,
    override val dataStore: IsDataStore,
) : StoreConnection {
    override val type: StoreType = StoreType.ROCKS_DB

    override fun close() {
        runBlocking {
            runCatching { dataStore.closeAllListeners() }
            runCatching { dataStore.close() }
        }
    }
}

data class FoundationDbStoreConnection(
    val directoryPath: List<String>,
    val clusterFilePath: String?,
    val tenantName: String?,
    override val dataStore: IsDataStore,
) : StoreConnection {
    override val type: StoreType = StoreType.FOUNDATION_DB

    override fun close() {
        runBlocking {
            runCatching { dataStore.closeAllListeners() }
            runCatching { dataStore.close() }
        }
    }
}
