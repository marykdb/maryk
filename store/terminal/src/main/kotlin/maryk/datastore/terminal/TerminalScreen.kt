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

        Spacer(modifier = Modifier.height(1))

        OutputView(state)

        Spacer(modifier = Modifier.height(1))

        LogView(state)

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
    state.connectionDescription.value?.let {
        Text("Connected: $it")
    } ?: Text("No store connected. Use the wizard to configure a store.")
    if (state.isConnecting.value) {
        Text("Connecting...")
    }
}

@Composable
private fun LogView(state: TerminalState) {
    val lines = if (state.logLines.size <= 12) state.logLines else state.logLines.takeLast(12)
    Text("Logs:")
    if (lines.isEmpty()) {
        Text("  <none>")
    } else {
        lines.forEach { Text("  $it") }
    }
}

@Composable
private fun OutputView(state: TerminalState) {
    val title = state.outputTitle.value
    val lines = state.outputLines
    if (title == null && lines.isEmpty()) {
        Text("Output:")
        Text("  <none>")
        return
    }
    Text(title ?: "Output:")
    if (lines.isEmpty()) {
        Text("  <none>")
    } else {
        lines.forEach { Text("  $it") }
    }
}

@Composable
private fun StoreSelectionView(mode: UiMode.SelectStore) {
    Text("Select a store (↑/↓ then Enter):")
    StoreType.entries.forEachIndexed { index, storeType ->
        val prefix = if (index == mode.selectedIndex) "➤" else " "
        Text("$prefix ${storeType.displayName}")
    }
    Text("Commands are available after selecting and connecting to a store.")
}

@Composable
private fun ConfigureStoreView(mode: UiMode.ConfigureStore) {
    val field = mode.currentField
    Text("Configure ${mode.storeType.displayName} (${mode.fieldIndex + 1}/${mode.fields.size})")
    Text(field.label)
    field.description?.let { Text("  $it") }
    field.defaultValue?.takeIf { it.isNotEmpty() }?.let { Text("  Default: $it") }
    Text("  Input: ${mode.input}")
    Text("Press Enter to confirm, Tab to accept default, or Esc to cancel.")
}

@Composable
private fun PromptView(mode: UiMode.Prompt) {
    Row {
        Text("> ")
        Text(mode.input)
    }
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
