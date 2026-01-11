package io.maryk.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val panePadding = 10.dp
private val denseFieldHeight = 28.dp
private val denseButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
private val denseIconSize = 14.dp
private val denseIconButtonSize = 26.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoresWindowContent(
    storesState: StoresState,
    openStoreIds: Set<String>,
    onOpenBrowser: (StoreDefinition) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stores") },
                actions = {
                    SmallButton(
                        label = "Add store",
                        icon = Icons.Default.Add,
                        colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onTertiary, containerColor = MaterialTheme.colorScheme.tertiary),
                        onClick = { storesState.openStoreEditor(null) },
                    )
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(panePadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (storesState.stores.isEmpty()) {
                    EmptyState(
                        title = "No stores yet",
                        message = "Add a RocksDB or FoundationDB store to get started.",
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(storesState.stores, key = { it.id }) { store ->
                            StoreRow(
                                store = store,
                                isOpen = openStoreIds.contains(store.id),
                                onOpen = { onOpenBrowser(store) },
                                onEdit = { storesState.openStoreEditor(store) },
                                onRemove = { storesState.removeStore(store) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (storesState.showStoreEditor) {
        StoreEditorDialog(storesState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserWindowContent(
    state: BrowserState,
    onClose: () -> Unit,
) {
    Browser(
        state = state,
        onClose = onClose,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreRow(
    store: StoreDefinition,
    isOpen: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember(store.id) { mutableStateOf(false) }
    val connectedColor = Color(0xFF3FB950)
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(store.id) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                menuExpanded = true
                            }
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onDoubleClick = { if (!isOpen) onOpen() },
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                Text(store.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${store.type.label} • ${store.displayLocation()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isOpen) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(connectedColor, shape = CircleShape)
                            .border(1.dp, connectedColor.copy(alpha = 0.4f), shape = CircleShape),
                    )
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove") },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StoreEditorDialog(storesState: StoresState) {
    val editing = storesState.editingStore
    var name by remember(editing) { mutableStateOf(editing?.name.orEmpty()) }
    var type by remember(editing) { mutableStateOf(editing?.type ?: StoreKind.ROCKS_DB) }
    var directory by remember(editing) { mutableStateOf(editing?.directory.orEmpty()) }
    var clusterFile by remember(editing) { mutableStateOf(editing?.clusterFile.orEmpty()) }
    var tenant by remember(editing) { mutableStateOf(editing?.tenant.orEmpty()) }
    var error by remember(editing) { mutableStateOf<String?>(null) }

    ModalSurface(onDismiss = { storesState.closeStoreEditor() }) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) "Add store" else "Edit store", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                SmallIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close",
                    onClick = { storesState.closeStoreEditor() },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name",
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Store type", style = MaterialTheme.typography.labelMedium)
                    StoreTypeTabs(
                        selected = type,
                        onSelected = { type = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SmallOutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it },
                    label = if (type == StoreKind.ROCKS_DB) "RocksDB directory" else "FDB directory path (slash-separated)",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == StoreKind.FOUNDATION_DB) {
                    SmallOutlinedTextField(
                        value = clusterFile,
                        onValueChange = { clusterFile = it },
                        label = "Cluster file (optional)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SmallOutlinedTextField(
                        value = tenant,
                        onValueChange = { tenant = it },
                        label = "Tenant (optional)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallOutlinedButton(
                    label = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { storesState.closeStoreEditor() },
                )
                SmallButton(
                    label = "Save",
                    icon = Icons.Default.Edit,
                    colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onTertiary, containerColor = MaterialTheme.colorScheme.tertiary),
                    onClick = {
                        val trimmedName = name.trim()
                        val trimmedDirectory = directory.trim()
                        if (trimmedName.isBlank()) {
                            error = "Name is required."
                            return@SmallButton
                        }
                        if (trimmedDirectory.isBlank()) {
                            error = "Directory is required."
                            return@SmallButton
                        }
                        val newStore = StoreDefinition(
                            id = editing?.id ?: generateStoreId(),
                            name = trimmedName,
                            type = type,
                            directory = trimmedDirectory,
                            clusterFile = clusterFile.trim().ifBlank { null },
                            tenant = tenant.trim().ifBlank { null },
                        )
                        storesState.upsertStore(newStore)
                        storesState.closeStoreEditor()
                    },
                )
            }
        }
    }
}

@Composable
private fun StoreTypeTabs(
    selected: StoreKind,
    onSelected: (StoreKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .border(1.dp, colors.outline.copy(alpha = 0.35f), shape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoreTypeTab(
            label = "RocksDB",
            selected = selected == StoreKind.ROCKS_DB,
            onClick = { onSelected(StoreKind.ROCKS_DB) },
            modifier = Modifier.weight(1f),
        )
        StoreTypeTab(
            label = "FoundationDB",
            selected = selected == StoreKind.FOUNDATION_DB,
            onClick = { onSelected(StoreKind.FOUNDATION_DB) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StoreTypeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val background = if (selected) colors.tertiary else Color.Transparent
    val textColor = if (selected) colors.onTertiary else colors.onSurface
    Box(
        modifier = modifier
            .height(26.dp)
            .background(background, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

@Composable
private fun SmallButton(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors ?: ButtonDefaults.buttonColors(),
        contentPadding = denseButtonPadding,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(denseIconSize))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SmallOutlinedButton(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = denseButtonPadding,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(denseIconSize))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(denseIconButtonSize),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(denseIconSize))
    }
}

@Composable
private fun SmallOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
) {
    val shape = MaterialTheme.shapes.small
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Surface(
            shape = shape,
            color = colors.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(denseFieldHeight)
                .border(1.dp, colors.outline.copy(alpha = 0.4f), shape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(denseFieldHeight)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(denseIconSize))
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = singleLine,
                        textStyle = textStyle.copy(color = colors.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (value.isBlank() && placeholder != null) {
                        Text(
                            placeholder,
                            style = textStyle,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun generateStoreId(): String {
    val bytes = ByteArray(8)
    repeat(bytes.size) { idx ->
        bytes[idx] = Random.nextInt(0, 256).toByte()
    }
    return bytes.joinToString(separator = "") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
}

@Composable
fun MarykTheme(
    useDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val darkScheme = darkColorScheme(
        primary = Color(0xFF8E7CF6),
        secondary = Color(0xFF8E7CF6),
        tertiary = Color(0xFF8E7CF6),
        surface = Color(0xFF0F1416),
        surfaceVariant = Color(0xFF1B2328),
        background = Color(0xFF0D1114),
        error = Color(0xFFFF6B6B),
    )
    val lightScheme = lightColorScheme(
        primary = Color(0xFF6B5CE6),
        secondary = Color(0xFF6B5CE6),
        tertiary = Color(0xFF6B5CE6),
        surfaceVariant = Color(0xFFEAEFF1),
        surface = Color(0xFFF6F8F9),
    )
    val colorScheme = if (useDark) darkScheme else lightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography.copy(
            bodySmall = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
            labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
            labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
            titleSmall = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
        ),
        content = content,
    )
}
