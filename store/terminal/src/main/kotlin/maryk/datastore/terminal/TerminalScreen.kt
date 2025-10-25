package maryk.datastore.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Spacer
import com.jakewharton.mosaic.ui.Text
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import maryk.datastore.terminal.driver.StoreType

@Composable
fun TerminalScreen(
    state: TerminalState,
    controller: TerminalController,
) {
    val mode by state.mode
    val terminal = LocalTerminalState.current
    val totalColumns = terminal.size.columns.coerceAtLeast(1)
    val totalRows = terminal.size.rows.coerceAtLeast(1)
    val banner = state.bannerMessage.value
    val historyWindow = state.currentHistoryWindow()
    val activeEntry = state.activeHistoryEntry()
    val historyRows = historyLineCount(historyWindow)
    val activeBaseRows = activeBaseLineCount(activeEntry, historyWindow.totalCount)
    val modeRows = modeLineCount(mode)
    val bannerRows = if (banner != null) 2 else 0
    val staticRows = 2 + 2 + 1 + bannerRows + historyRows + 1 + activeBaseRows + 1 + modeRows
    val detailCapacity = (totalRows - staticRows).coerceAtLeast(1)

    LaunchedEffect(detailCapacity) {
        state.updateDetailLinesPerPage(detailCapacity)
    }

    DisposableEffect(Unit) {
        onDispose { state.close() }
    }

    Column(
        modifier = Modifier
            .width(totalColumns)
            .height(totalRows)
            .padding(horizontal = 2, vertical = 1)
            .onKeyEvent { controller.handleKey(it) },
    ) {
        Header(state)

        banner?.let { message ->
            Spacer(modifier = Modifier.height(1))
            BannerView(message)
        }

        Spacer(modifier = Modifier.height(1))
        HistoryView(state)

        Spacer(modifier = Modifier.height(1))
        ActiveResponseView(state, totalColumns)

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
private fun ActiveResponseView(state: TerminalState, totalColumns: Int) {
    Text(coloredSubheading("Active response"))
    val entry = state.activeHistoryEntry()
    val lineOffset by state.activeLineOffset
    val detailPageSize by state.detailLinesPerPage
    if (entry == null) {
        Text("  No responses yet. Run 'help' to see available commands.")
        return
    }
    val scanState = entry.scanState
    val icon = entry.style.icon()
    val heading = entry.heading ?: entry.summary
    val commandLabel = entry.label ?: "system"
    Text(colorize("  $icon $heading ", entry.style.foreground(), entry.style.background(), bold = true))
    Text("  Command: $commandLabel")
    if (scanState != null) {
        ScanResponseView(entry, scanState, detailPageSize, totalColumns)
    } else {
        val lines = entry.visibleLines(lineOffset, detailPageSize)
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
}

@Composable
private fun ScanResponseView(
    entry: HistoryEntry,
    scan: ScanSession,
    detailPageSize: Int,
    totalColumns: Int,
) {
    if (scan.rows.isEmpty()) {
        Text("    <no rows>")
        return
    }

    when (scan.displayMode.value) {
        ScanDisplayMode.List -> {
            val start = scan.listOffset.value.coerceAtLeast(0)
            val end = (start + detailPageSize).coerceAtMost(scan.rows.size)
            val visible = scan.rows.subList(start, end)
            visible.forEachIndexed { index, row ->
                val absoluteIndex = start + index
                val isSelected = absoluteIndex == scan.selectedIndex.value
                val prefix = if (isSelected) "➤" else " "
                val summaryText = row.summary.joinToString(" | ") { (name, value) -> "$name=$value" }
                val baseLine = "$prefix ${row.keyBase64} | $summaryText"
                val truncated = truncateToWidth(baseLine, totalColumns - 4)
                val content = "  $truncated"
                if (isSelected) {
                    Text(colorize(" $content ", entry.style.foreground(), entry.style.background(), bold = true))
                } else {
                    Text(content)
                }
            }
            val total = scan.rows.size
            val lastVisible = (start + visible.size).coerceAtMost(total)
            Text("    Rows ${start + 1}-$lastVisible of $total — use ↑/↓ to navigate")
            if (scan.nextStartAfterKey != null) {
                Text("    Reach the end to load more rows automatically.")
            }
        }
        ScanDisplayMode.Detail -> {
            val row = scan.currentRow()
            if (row == null) {
                Text("    <no selection>")
                return
            }
            val offset = scan.yamlOffset.value.coerceAtLeast(0)
            val lines = row.yamlLines.drop(offset).take(detailPageSize)
            lines.forEach { line ->
                val truncated = truncateToWidth(line, totalColumns - 4)
                Text("    $truncated")
            }
            val total = row.yamlLines.size
            val lastVisible = (offset + lines.size).coerceAtMost(total)
            Text("    Lines ${offset + 1}-${lastVisible} of $total — use ↑/↓ to scroll")
        }
    }
}

private fun truncateToWidth(text: String, maxWidth: Int): String {
    if (maxWidth <= 0) return text
    return if (text.length <= maxWidth) {
        text
    } else {
        if (maxWidth <= 1) "…" else text.take(maxWidth - 1).trimEnd() + "…"
    }
}

@Composable
private fun HistoryView(state: TerminalState) {
    Text(coloredSubheading("Recent responses"))
    val selectedIndex by state.selectedHistoryIndex
    val window = state.currentHistoryWindow()
    if (window.totalCount == 0) {
        Text("  No history yet.")
        return
    }

    if (window.newerCount > 0) {
        Text("  ↑ ${window.newerCount} newer (PgDn)")
    }

    window.entries.forEachIndexed { offset, entry ->
        val index = window.startIndex + offset
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

    if (window.olderCount > 0) {
        Text("  ↓ ${window.olderCount} older (PgUp)")
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
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }
    Row {
        Text("> ")
        val cursor = if (cursorVisible) "▋" else " "
        Text(mode.input + cursor)
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

private fun historyLineCount(window: HistoryWindowState): Int {
    var count = 1 // heading
    if (window.totalCount == 0) {
        return count + 1
    }
    count += window.entries.size
    if (window.newerCount > 0) count += 1
    if (window.olderCount > 0) count += 1
    return count
}

private fun activeBaseLineCount(entry: HistoryEntry?, historySize: Int): Int {
    if (entry == null) return 2
    val base = 3 // heading, summary, command
    val trailing = 1 // indicator or "no additional details" message
    val historyHint = if (historySize > 1) 1 else 0
    return base + trailing + historyHint
}

private fun modeLineCount(mode: UiMode): Int = when (mode) {
    is UiMode.SelectStore -> 1 + StoreType.entries.size + 1
    is UiMode.ConfigureStore -> {
        var count = 4 // configure line, label, input, instructions
        if (!mode.currentField.description.isNullOrEmpty()) count += 1
        if (!mode.currentField.defaultValue.isNullOrEmpty()) count += 1
        count
    }
    is UiMode.Prompt -> 3
    is UiMode.ModelSelection -> if (mode.models.isEmpty()) 1 else 1 + mode.models.size
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
