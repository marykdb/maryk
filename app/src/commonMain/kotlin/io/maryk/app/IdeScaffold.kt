package io.maryk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private val topBarHeight = 60.dp

@Composable
fun AppScaffold(
    state: BrowserState,
    uiState: BrowserUiState,
    onToggleCatalog: () -> Unit,
    onToggleInspector: () -> Unit,
    content: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().height(topBarHeight).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(onClick = onToggleCatalog, modifier = Modifier.size(32.dp)) {
                        val icon = if (uiState.showCatalog) {
                            Icons.AutoMirrored.Filled.ViewSidebar
                        } else {
                            Icons.AutoMirrored.Outlined.ViewSidebar
                        }
                        Icon(
                            icon,
                            contentDescription = "Toggle catalog",
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                            modifier = Modifier.scale(scaleX = -1f, scaleY = 1f),
                        )
                    }
                    StoreChrome(state = state)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onToggleInspector, modifier = Modifier.size(32.dp)) {
                        val icon = if (uiState.showInspector) {
                            Icons.AutoMirrored.Filled.ViewSidebar
                        } else {
                            Icons.AutoMirrored.Outlined.ViewSidebar
                        }
                        Icon(
                            icon,
                            contentDescription = "Toggle inspector",
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
            state.lastActionMessage?.let { message ->
                val isError = message.contains("failed", ignoreCase = true) || message.contains("error", ignoreCase = true)
                if (isError) {
                    LaunchedEffect(message) {
                        System.err.println(message)
                    }
                    ErrorBanner(
                        message = message,
                        onDismiss = { state.clearLastActionMessage() },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    )
                } else {
                    SuccessToast(
                        message = message,
                        onDismiss = { state.clearLastActionMessage() },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    )
                }
            }
            state.exportToastMessage?.let { message ->
                LaunchedEffect(message) {
                    delay(2800)
                    state.clearExportToast()
                }
                ExportToast(
                    message = message,
                    onDismiss = { state.clearExportToast() },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                )
            }
        }
        bottomBar()
    }

}

@Composable
private fun StoreChrome(
    state: BrowserState,
) {
    val connection = state.activeConnection
    val storeName = connection?.definition?.name ?: "No store"
    val definition = connection?.definition
    val location = definition?.displayLocation() ?: "Not connected"
    val envLabel = definition?.type?.label ?: "Env"
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier.width(520.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            storeName,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    envLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                location,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(location)) },
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy store location",
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
fun ModalSurface(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.62f).padding(top = 80.dp),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun SuccessToast(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(message) {
        delay(3000)
        onDismiss()
    }
    Surface(
        color = Color(0xFF2E7D32),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(message, style = MaterialTheme.typography.labelSmall, color = Color.White)
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ExportToast(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
