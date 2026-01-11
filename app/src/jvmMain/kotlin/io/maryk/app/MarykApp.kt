@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.maryk.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlin.uuid.Uuid

fun main() = application {
    val repository = remember { StoreRepository() }
    val storesState = remember { StoresState(repository) }
    val sessions = remember { mutableStateListOf<BrowserSession>() }

    LaunchedEffect(Unit) {
        storesState.loadStores()
    }

    MarykTheme {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Maryk Stores",
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
            Window(
                onCloseRequest = { sessions.remove(session) },
                title = "Maryk - ${session.store.name}",
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
