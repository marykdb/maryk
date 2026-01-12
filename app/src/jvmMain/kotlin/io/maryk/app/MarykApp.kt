@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.maryk.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Toolkit
import kotlin.uuid.Uuid

fun main() = application {
    val repository = remember { StoreRepository() }
    val storesState = remember { StoresState(repository) }
    val storesWindowState = rememberWindowState(width = 600.dp, height = 700.dp)

    val sessions = remember { mutableStateListOf<BrowserSession>() }
    val screenSize = remember { Toolkit.getDefaultToolkit().screenSize }
    val defaultWidth = (screenSize.width * 0.9).toInt().coerceAtLeast(1100).coerceAtMost(1600)
    val defaultHeight = (screenSize.height * 0.9).toInt().coerceAtLeast(800).coerceAtMost(1200)

    LaunchedEffect(Unit) {
        storesState.loadStores()
    }

    MarykTheme {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Maryk Stores",
            state = storesWindowState,
        ) {
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

        sessions.forEach { session ->
            val sessionWindowState = rememberWindowState(width = defaultWidth.dp, height = defaultHeight.dp)
            Window(
                onCloseRequest = { sessions.remove(session) },
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

                BrowserWindowContent(
                    state = browserState,
                    onClose = { sessions.remove(session) },
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
