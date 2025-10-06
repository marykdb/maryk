package maryk.datastore.terminal

import com.apple.foundationdb.tuple.Tuple
import maryk.datastore.terminal.driver.StoreType

/**
 * Connection information provided either through command line arguments or the interactive wizard.
 */
sealed interface StoreConnectionConfig {
    val type: StoreType

    data class RocksDb(
        val path: String,
    ) : StoreConnectionConfig {
        override val type: StoreType = StoreType.RocksDb
    }

    data class FoundationDb(
        val clusterFile: String?,
        val tenant: Tuple?,
        val directoryRoot: List<String>,
        val apiVersion: Int,
    ) : StoreConnectionConfig {
        override val type: StoreType = StoreType.FoundationDb
    }
}
