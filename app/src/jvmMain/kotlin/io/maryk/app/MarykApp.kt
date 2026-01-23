@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.maryk.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import java.awt.Toolkit
import kotlin.uuid.Uuid

fun main() = application {
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

        sessions.forEach { session ->
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

private fun generateSessionId(): String {
    return Uuid.random().toString()
}
