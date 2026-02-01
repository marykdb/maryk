package io.maryk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun CatalogDrawer(
    state: BrowserState,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    val models = state.models
    val filtered = models.filter { model -> fuzzyMatch(model.name, search) }

    Column(
        modifier = modifier.fillMaxHeight().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Catalog", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
        QuerySearchField(
            value = search,
            onValueChange = { search = it },
            placeholder = "Search models",
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { "model-${it.id}" }) { model ->
                CatalogRow(state, model)
            }
        }
    }
}

@Composable
private fun CatalogRow(
    state: BrowserState,
    model: ModelEntry,
) {
    val selected = state.selectedModelId == model.id
    val count = state.modelRowCount(model.id)
    val countLabel = when {
        count == null -> "…"
        count.capped -> "100+"
        else -> count.count.toString()
    }
    Surface(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (selected) 1.dp else 0.dp,
    ) {
        ContextMenuArea(
            items = {
                listOf(
                    ContextMenuItem("Export model…") { state.requestExportModelDialog(model.id) },
                    ContextMenuItem("Export model data…") { state.requestExportModelDataDialog(model.id) },
                )
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .handPointer().clickable { state.selectModel(model.id) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "ID ${model.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Badge("v1", modifier = Modifier.alignByBaseline())
                        if (model.name.contains("deprecated", ignoreCase = true)) {
                            Badge("WARN", modifier = Modifier.alignByBaseline())
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Badge(countLabel, modifier = Modifier.alignByBaseline())
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private fun fuzzyMatch(text: String, query: String): Boolean {
    if (query.isBlank()) return true
    var index = 0
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    lowerQuery.forEach { char ->
        index = lowerText.indexOf(char, startIndex = index)
        if (index == -1) return false
        index += 1
    }
    return true
}