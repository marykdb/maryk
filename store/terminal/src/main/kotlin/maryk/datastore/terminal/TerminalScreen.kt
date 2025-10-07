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
import kotlin.math.max

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
        HistoryView(state)

        Spacer(modifier = Modifier.height(1))
        ActiveResponseView(state)

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
    Text(coloredHeading("Maryk DataStore Terminal"))
    val statusLabel: String
    val fg: AnsiColor
    val bg: AnsiColor
    val details: String
    when {
        state.isConnecting.value -> {
            statusLabel = " Connecting "
            fg = AnsiColor.Black
            bg = AnsiColor.BrightYellow
            details = "Attempting to connect…"
        }
        state.connectionDescription.value != null -> {
            statusLabel = " Connected "
            fg = AnsiColor.White
            bg = AnsiColor.BrightGreen
            details = state.connectionDescription.value ?: ""
        }
        else -> {
            statusLabel = " Disconnected "
            fg = AnsiColor.White
            bg = AnsiColor.BrightRed
            details = "Run the wizard to connect to a store."
        }
    }
    val coloredStatus = colorize(statusLabel, fg, bg, bold = true)
    val detailSuffix = if (details.isNotEmpty()) " $details" else ""
    Text(coloredStatus + detailSuffix)
}

@Composable
private fun BannerView(banner: BannerMessage) {
    val icon = banner.style.icon()
    Text(colorize(" $icon ${banner.message} ", banner.style.foreground(), banner.style.background(), bold = true))
}

@Composable
private fun ActiveResponseView(state: TerminalState) {
    Text(coloredSubheading("Active response"))
    val entry = state.activeHistoryEntry()
    val lineOffset by state.activeLineOffset
    if (entry == null) {
        Text("  No responses yet. Run 'help' to see available commands.")
        return
    }
    val icon = entry.style.icon()
    val heading = entry.heading ?: entry.summary
    val commandLabel = entry.label ?: "system"
    Text(colorize("  $icon $heading ", entry.style.foreground(), entry.style.background(), bold = true))
    Text("  Command: $commandLabel")
    val lines = entry.visibleLines(lineOffset)
    if (lines.isEmpty()) {
        Text("    (no additional details)")
    } else {
        lines.forEach { line -> Text("    $line") }
        val totalLines = entry.lines.size
        val lastVisibleLine = (lineOffset + lines.size).coerceAtMost(totalLines)
        val indicator = "    Lines ${lineOffset + 1}-${lastVisibleLine} of $totalLines — use ↑/↓ to scroll"
        Text(indicator)
    }
    if (state.history.size > 1) {
        Text("    Use PgUp/PgDn to switch responses.")
    }
}

@Composable
private fun HistoryView(state: TerminalState) {
    Text(coloredSubheading("Recent responses"))
    val history = state.history
    val selectedIndex by state.selectedHistoryIndex
    if (history.isEmpty()) {
        Text("  No history yet.")
        return
    }

    val windowSize = 5
    val maxStart = max(0, history.size - windowSize)
    val windowStart = when {
        history.size <= windowSize -> 0
        selectedIndex <= 1 -> 0
        selectedIndex >= history.size - 2 -> maxStart
        else -> (selectedIndex - 2).coerceIn(0, maxStart)
    }
    val window = history.drop(windowStart).take(windowSize)

    if (windowStart > 0) {
        Text("  ↑ ${windowStart} newer (PgDn)")
    }

    window.forEachIndexed { offset, entry ->
        val index = windowStart + offset
        val isSelected = index == selectedIndex
        val indicator = if (isSelected) "➤" else " "
        val icon = entry.style.icon()
        val label = entry.label ?: "system"
        val summary = "$indicator $icon $label — ${entry.summary}"
        val text = if (isSelected) {
            colorize(" $summary ", entry.style.foreground(), entry.style.background(), bold = true)
        } else {
            summary
        }
        Text("  $text")
    }

    val remaining = history.size - (windowStart + window.size)
    if (remaining > 0) {
        Text("  ↓ $remaining older (PgUp)")
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
    Text("Use ↑/↓ to scroll response details, PgUp/PgDn to browse history, 'help' for commands.")
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

private fun coloredHeading(text: String): String =
    colorize(" $text ", AnsiColor.White, AnsiColor.BrightBlue, bold = true)

private fun coloredSubheading(text: String): String =
    colorize(" $text ", AnsiColor.White, AnsiColor.Blue, bold = true)

private fun colorize(
    text: String,
    foreground: AnsiColor? = null,
    background: AnsiColor? = null,
    bold: Boolean = false,
): String {
    val codes = mutableListOf<String>()
    if (bold) codes += "1"
    foreground?.let { codes += it.fgCode.toString() }
    background?.let { codes += it.bgCode.toString() }
    if (codes.isEmpty()) return text
    return "\u001B[${codes.joinToString(";")}]$text\u001B[0m"
}

private fun PanelStyle.foreground(): AnsiColor = when (this) {
    PanelStyle.Info -> AnsiColor.White
    PanelStyle.Success -> AnsiColor.White
    PanelStyle.Warning -> AnsiColor.Black
    PanelStyle.Error -> AnsiColor.White
}

private fun PanelStyle.background(): AnsiColor = when (this) {
    PanelStyle.Info -> AnsiColor.BrightBlue
    PanelStyle.Success -> AnsiColor.BrightGreen
    PanelStyle.Warning -> AnsiColor.BrightYellow
    PanelStyle.Error -> AnsiColor.BrightRed
}

private enum class AnsiColor(val fgCode: Int, val bgCode: Int) {
    Black(30, 40),
    Red(31, 41),
    Green(32, 42),
    Yellow(33, 43),
    Blue(34, 44),
    Magenta(35, 45),
    Cyan(36, 46),
    White(37, 47),
    BrightBlack(90, 100),
    BrightRed(91, 101),
    BrightGreen(92, 102),
    BrightYellow(93, 103),
    BrightBlue(94, 104),
    BrightMagenta(95, 105),
    BrightCyan(96, 106),
    BrightWhite(97, 107),
}
