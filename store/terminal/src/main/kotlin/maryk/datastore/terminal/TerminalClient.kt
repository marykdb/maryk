package maryk.datastore.terminal

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.apple.foundationdb.tuple.Tuple
import com.jakewharton.mosaic.runMosaicMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import maryk.datastore.terminal.driver.StoreType

fun main(args: Array<String>) {
    val config = parseConnectionArgs(args)

    runMosaicMain {
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        val initialMode = remember(config) {
            if (config != null) UiMode.Prompt(input = "") else UiMode.SelectStore(selectedIndex = 0)
        }
        val state = remember { TerminalState(initialMode) }
        val controller = remember { TerminalController(scope, state) { scope.cancel() } }

        DisposableEffect(Unit) {
            onDispose { scope.cancel() }
        }

        LaunchedEffect(config) {
            if (config != null) {
                state.recordHistory(
                    label = "startup",
                    heading = "Command line configuration",
                    lines = listOf("Using connection parameters from command line arguments."),
                    style = PanelStyle.Info,
                )
                controller.startWithConfig(config)
            } else {
                controller.beginWizard()
            }
        }

        TerminalScreen(state, controller)
    }
}

private fun parseConnectionArgs(args: Array<String>): StoreConnectionConfig? {
    var storeType: StoreType? = null
    var rocksPath: String? = null
    var clusterFile: String? = null
    var tenant: String? = null
    var directoryRoot: String? = null
    var apiVersion: Int? = null

    for (arg in args) {
        when {
            arg.equals("--rocksdb", ignoreCase = true) -> storeType = StoreType.RocksDb
            arg.equals("--foundationdb", ignoreCase = true) -> storeType = StoreType.FoundationDb
            arg.startsWith("--store=", ignoreCase = true) -> {
                storeType = when (arg.substringAfter('=').lowercase()) {
                    "rocksdb" -> StoreType.RocksDb
                    "foundationdb", "fdb" -> StoreType.FoundationDb
                    else -> storeType
                }
            }
            arg.startsWith("--rocksdb-path=", ignoreCase = true) -> {
                rocksPath = arg.substringAfter('=').trim().takeIf { it.isNotEmpty() }
                storeType = StoreType.RocksDb
            }
            arg.startsWith("--fdb-cluster=", ignoreCase = true) -> {
                clusterFile = arg.substringAfter('=').trim().takeIf { it.isNotEmpty() }
                storeType = StoreType.FoundationDb
            }
            arg.startsWith("--fdb-tenant=", ignoreCase = true) -> {
                tenant = arg.substringAfter('=').trim().takeIf { it.isNotEmpty() }
                storeType = StoreType.FoundationDb
            }
            arg.startsWith("--fdb-directory=", ignoreCase = true) -> {
                directoryRoot = arg.substringAfter('=').trim().takeIf { it.isNotEmpty() }
                storeType = StoreType.FoundationDb
            }
            arg.startsWith("--fdb-api-version=", ignoreCase = true) -> {
                apiVersion = arg.substringAfter('=').trim().toIntOrNull()
                storeType = StoreType.FoundationDb
            }
        }
    }

    return when (storeType) {
        StoreType.RocksDb -> rocksPath?.let { StoreConnectionConfig.RocksDb(it) }
        StoreType.FoundationDb -> {
            val directories = directoryRoot?.split(Regex("[\\s,/]+"))
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            val tuple = tenant?.let { Tuple.from(it) }
            StoreConnectionConfig.FoundationDb(
                clusterFile = clusterFile,
                tenant = tuple,
                directoryRoot = if (directories.isEmpty()) listOf("maryk") else directories,
                apiVersion = apiVersion ?: 730,
            )
        }
        null -> null
    }
}
