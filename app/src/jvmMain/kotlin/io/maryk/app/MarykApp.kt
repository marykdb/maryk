package io.maryk.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.maryk.app.config.StoreConnector
import io.maryk.app.config.StoreDefinition
import io.maryk.app.config.StoreRepository
import io.maryk.app.state.BrowserState
import io.maryk.app.state.StoresState
import java.awt.Toolkit
import kotlinx.coroutines.runBlocking
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.rocksdb.RocksDBDataStore
import kotlin.uuid.Uuid

const val PACKAGED_STORE_SMOKE_SUCCESS = "MARYK_PACKAGED_STORE_SMOKE_OK"

fun main(args: Array<String>) {
    val smokeDirectory = args.getOrNull(0).takeIf { it == "--smoke-store" }?.let {
        requireNotNull(args.getOrNull(1)) { "--smoke-store requires a directory" }
    }
    if (smokeDirectory != null) {
        println(runPackagedStoreSmoke(smokeDirectory))
    } else {
        runMarykApp()
    }
}

fun runPackagedStoreSmoke(directory: String): String = runBlocking {
    val values = PackagedStoreSmokeModel.create {
        id with 1u
        value with "packaged-store-smoke"
    }
    var dataStore = RocksDBDataStore.open(
        relativePath = directory,
        dataModelsById = mapOf(1u to PackagedStoreSmokeModel),
    )
    val key = try {
        val status = dataStore.execute(PackagedStoreSmokeModel.add(values)).statuses.single()
        @Suppress("UNCHECKED_CAST")
        (status as? AddSuccess<PackagedStoreSmokeModel>)?.key
            ?: error("Smoke write failed: $status")
    } finally {
        dataStore.close()
    }

    dataStore = RocksDBDataStore.open(
        relativePath = directory,
        dataModelsById = mapOf(1u to PackagedStoreSmokeModel),
    )
    try {
        val storedValue = dataStore.execute(PackagedStoreSmokeModel.get(key)).values.single()
            .values { value }
        check(storedValue == "packaged-store-smoke") { "Smoke read returned $storedValue" }
    } finally {
        dataStore.close()
    }
    PACKAGED_STORE_SMOKE_SUCCESS
}

private object PackagedStoreSmokeModel : RootDataModel<PackagedStoreSmokeModel>(
    keyDefinition = {
        PackagedStoreSmokeModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val value by string(index = 2u)
}

private fun runMarykApp() = application {
    val repository = remember { StoreRepository() }
    val storesState = remember { StoresState(repository) }
    val storesWindowState = rememberWindowState(
        width = 600.dp,
        height = 700.dp,
        position = WindowPosition(Alignment.Center),
    )

    val sessions = remember { mutableStateListOf<BrowserSession>() }
    val storesWindowOpen = remember { mutableStateOf(true) }
    val isMac = remember { System.getProperty("os.name").contains("Mac", ignoreCase = true) }
    val screenSize = remember { Toolkit.getDefaultToolkit().screenSize }
    val defaultWidth = (screenSize.width * 0.9).toInt().coerceAtLeast(1100).coerceAtMost(1600)
    val defaultHeight = (screenSize.height * 0.9).toInt().coerceAtLeast(800).coerceAtMost(1200)

    LaunchedEffect(Unit) {
        storesState.loadStores()
    }

    MarykTheme {
        val shortcutClose = remember(isMac) { if (isMac) KeyShortcut(Key.W, meta = true) else KeyShortcut(Key.W, ctrl = true) }
        val shortcutNew = remember(isMac) { if (isMac) KeyShortcut(Key.N, meta = true) else KeyShortcut(Key.N, ctrl = true) }
        val shortcutReload = remember(isMac) { if (isMac) KeyShortcut(Key.R, meta = true) else KeyShortcut(Key.R, ctrl = true) }

        fun openStoreEditor() {
            if (!storesWindowOpen.value) {
                storesWindowOpen.value = true
            }
            storesState.openStoreEditor(null)
        }

        fun closeStoresWindow() {
            if (sessions.isEmpty()) {
                exitApplication()
            } else {
                storesWindowOpen.value = false
            }
        }

        fun closeBrowserWindow(session: BrowserSession) {
            sessions.remove(session)
            if (sessions.isEmpty() && !storesWindowOpen.value) {
                exitApplication()
            }
        }

        if (storesWindowOpen.value) {
            Window(
                onCloseRequest = { closeStoresWindow() },
                title = "Maryk Stores",
                state = storesWindowState,
            ) {
                MenuBar {
                    Menu("File") {
                        Item("Close Window", onClick = { closeStoresWindow() }, shortcut = shortcutClose)
                    }
                    Menu("Stores") {
                        Item("New Store", onClick = { openStoreEditor() }, shortcut = shortcutNew)
                        Item("Reload Stores", onClick = { storesState.loadStores() }, shortcut = shortcutReload)
                    }
                }
                StoresWindowContent(
                    storesState = storesState,
                    openStoreIds = sessions.map { it.store.id }.toSet(),
                    onOpenBrowser = { store ->
                        if (sessions.none { it.store.id == store.id }) {
                            sessions.add(BrowserSession(generateSessionId(), store))
                        }
                    },
                )
            }
        }

        BrowserSessionScopes(sessions, { it.id }) { session ->
            val sessionWindowState = rememberWindowState(
                width = defaultWidth.dp,
                height = defaultHeight.dp,
                position = WindowPosition(Alignment.Center),
            )
            Window(
                onCloseRequest = { closeBrowserWindow(session) },
                title = "Maryk - ${session.store.name}",
                state = sessionWindowState,
            ) {
                val scope = rememberCoroutineScope()
                val connector = remember { StoreConnector() }
                val browserState = remember(session.id) { BrowserState(connector, scope) }

                LaunchedEffect(session.id) {
                    browserState.connect(session.store)
                }

                DisposableEffect(Unit) {
                    onDispose { browserState.disconnect() }
                }

                MenuBar {
                    Menu("File") {
                        Item("Close Window", onClick = { closeBrowserWindow(session) }, shortcut = shortcutClose)
                    }
                    Menu("Data") {
                        Item("Reload Results", onClick = { browserState.scanFromStart() }, shortcut = shortcutReload)
                        Item("Import data…", onClick = { browserState.requestImportDataDialog() })
                        Item("Export all models…", onClick = { browserState.requestExportAllDialog() })
                        Item("Export data…", onClick = { browserState.requestExportDataDialog() })
                    }
                    Menu("Operations") {
                        Item("Migrations…", onClick = { browserState.requestMigrationDialog() })
                    }
                    Menu("Stores") {
                        Item("New Store", onClick = { openStoreEditor() }, shortcut = shortcutNew)
                        if (!storesWindowOpen.value) {
                            Item("Show Stores", onClick = { storesWindowOpen.value = true })
                        }
                    }
                }

                BrowserWindowContent(
                    state = browserState,
                    onClose = { closeBrowserWindow(session) },
                )
            }
        }
    }
}

private data class BrowserSession(
    val id: String,
    val store: StoreDefinition,
)

@Composable
internal fun <S> BrowserSessionScopes(
    sessions: Iterable<S>,
    sessionId: (S) -> String,
    content: @Composable (S) -> Unit,
) {
    sessions.forEach { session ->
        key(sessionId(session)) {
            content(session)
        }
    }
}

private fun generateSessionId(): String {
    return Uuid.random().toString()
}
