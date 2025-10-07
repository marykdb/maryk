package maryk.datastore.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Spacer
import com.jakewharton.mosaic.ui.Text
import kotlinx.coroutines.awaitCancellation
import maryk.datastore.terminal.driver.StoreType

@Composable
fun TerminalScreen(
    state: TerminalState,
    controller: TerminalController,
) {
    val mode by state.mode

    DisposableEffect(Unit) {
        onDispose { state.close() }
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 2, vertical = 1)
            .onKeyEvent { controller.handleKey(it) },
    ) {
        Header(state)

        state.bannerMessage.value?.let { banner ->
            Spacer(modifier = Modifier.height(1))
            BannerView(banner)
        }

        Spacer(modifier = Modifier.height(1))
        ActiveResponseView(state)

        Spacer(modifier = Modifier.height(1))
        HistoryView(state)

        Spacer(modifier = Modifier.height(1))
        when (val currentMode = mode) {
            is UiMode.SelectStore -> StoreSelectionView(currentMode)
            is UiMode.ConfigureStore -> ConfigureStoreView(currentMode)
            is UiMode.Prompt -> PromptView(currentMode)
            is UiMode.ModelSelection -> ModelSelectionView(currentMode)
        }
    }

    if (!state.shouldExit.value) {
        LaunchedEffect(Unit) { awaitCancellation() }
    }
}

@Composable
private fun Header(state: TerminalState) {
    Text("Maryk DataStore Terminal")
    val connection = when {
        state.isConnecting.value -> "Status: Connecting…"
        state.connectionDescription.value != null -> "Status: Connected — ${state.connectionDescription.value}"
        else -> "Status: Not connected"
    }
    Text(connection)
}

@Composable
private fun BannerView(banner: BannerMessage) {
    val icon = banner.style.icon()
    Text("$icon ${banner.message}")
}

@Composable
private fun ActiveResponseView(state: TerminalState) {
    Text("Active response:")
    val entry = state.activeHistoryEntry()
    val pageIndex by state.activePageIndex
    if (entry == null) {
        Text("  No responses yet. Run 'help' to see available commands.")
        return
    }
    val icon = entry.style.icon()
    val heading = entry.heading ?: entry.summary
    val commandLabel = entry.label ?: "system"
    Text("  $icon $heading")
    Text("  Command: $commandLabel")
    val lines = entry.page(pageIndex)
    if (lines.isEmpty()) {
        Text("    (no additional details)")
    } else {
        lines.forEach { line -> Text("    $line") }
    }
    val totalPages = entry.totalPages()
    if (totalPages > 1) {
        Text("    Page ${pageIndex + 1} of $totalPages — use PgUp/PgDn to scroll")
    }
}

@Composable
private fun HistoryView(state: TerminalState) {
    Text("Recent responses:")
    val history = state.history
    val selectedIndex by state.selectedHistoryIndex
    if (history.isEmpty()) {
        Text("  No history yet.")
        return
    }

    history.withIndex().take(5).forEach { (index, entry) ->
        val indicator = if (index == selectedIndex) "➤" else " "
        val icon = entry.style.icon()
        val label = entry.label ?: "system"
        Text("  $indicator $icon $label — ${entry.summary}")
    }
    if (history.size > 5) {
        Text("  … ${history.size - 5} more (use ↑/↓ to browse)")
    }
}

@Composable
private fun StoreSelectionView(mode: UiMode.SelectStore) {
    Text("Select a store (↑/↓ then Enter):")
    StoreType.entries.forEachIndexed { index, storeType ->
        val prefix = if (index == mode.selectedIndex) "➤" else " "
        Text("$prefix ${storeType.displayName}")
    }
    Text("Commands become available after connecting to a store.")
}

@Composable
private fun ConfigureStoreView(mode: UiMode.ConfigureStore) {
    val field = mode.currentField
    Text("Configure ${mode.storeType.displayName} (${mode.fieldIndex + 1}/${mode.fields.size})")
    Text(field.label)
    field.description?.let { Text("  $it") }
    field.defaultValue?.takeIf { it.isNotEmpty() }?.let { Text("  Default: $it") }
    Text("  Input: ${mode.input}")
    Text("Press Enter to confirm, Tab to accept default, Esc to switch store.")
}

@Composable
private fun PromptView(mode: UiMode.Prompt) {
    Text("Enter a command:")
    Row {
        Text("> ")
        Text(mode.input + "█")
    }
    Text("Use ↑/↓ to navigate responses, PgUp/PgDn to scroll details, 'help' for commands.")
}

@Composable
private fun ModelSelectionView(mode: UiMode.ModelSelection) {
    if (mode.models.isEmpty()) {
        Text("No models available.")
        return
    }
    Text("Select a model (↑/↓, Enter, Esc to cancel):")
    mode.models.forEachIndexed { index, model ->
        val prefix = if (index == mode.selectedIndex) "➤" else " "
        Text("$prefix ${model.name} (v${model.version})")
    }
}

private fun PanelStyle.icon(): String = when (this) {
    PanelStyle.Info -> "ℹ"
    PanelStyle.Success -> "✔"
    PanelStyle.Warning -> "⚠"
    PanelStyle.Error -> "✖"
}
