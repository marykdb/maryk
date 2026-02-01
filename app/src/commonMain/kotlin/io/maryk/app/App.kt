package io.maryk.app

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.vector.ImageVector
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
                    SmallOutlinedButton(
                        label = "Add store",
                        icon = Icons.Default.Add,
                        tint = MaterialTheme.colorScheme.primary,
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
    val connectedColor = Color(0xFF2DBE6C)
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
    ) {
        ContextMenuArea(
            items = {
                listOf(
                    ContextMenuItem("Edit", onEdit),
                    ContextMenuItem("Remove", onRemove),
                )
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .handPointer().combinedClickable(
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
    var sshHost by remember(editing) { mutableStateOf(editing?.sshHost.orEmpty()) }
    var sshUser by remember(editing) { mutableStateOf(editing?.sshUser.orEmpty()) }
    var sshPort by remember(editing) { mutableStateOf(editing?.sshPort?.toString().orEmpty()) }
    var sshLocalPort by remember(editing) { mutableStateOf(editing?.sshLocalPort?.toString().orEmpty()) }
    var sshIdentityFile by remember(editing) { mutableStateOf(editing?.sshIdentityFile.orEmpty()) }
    val initialUseSsh = editing?.let {
        it.sshHost?.isNotBlank() == true ||
            it.sshUser?.isNotBlank() == true ||
            it.sshPort != null ||
            it.sshLocalPort != null ||
            it.sshIdentityFile?.isNotBlank() == true
    } == true
    var useSsh by remember(editing) { mutableStateOf(initialUseSsh) }
    var error by remember(editing) { mutableStateOf<String?>(null) }
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(editing) {
        if (editing == null) {
            nameFocusRequester.requestFocus()
        }
    }

    ModalSurface(onDismiss = { storesState.closeStoreEditor() }) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) "Add store" else "Edit store", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SmallIconButton(modifier = Modifier.handPointer(), 
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
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Store type", style = MaterialTheme.typography.labelMedium)
                    StoreTypeTabs(
                        selected = type,
                        onSelected = { type = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val locationLabel = when (type) {
                    StoreKind.ROCKS_DB -> "RocksDB directory"
                    StoreKind.FOUNDATION_DB -> "FDB directory path (slash-separated)"
                    StoreKind.REMOTE -> "Remote URL (http://...)"
                }
                SmallOutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it },
                    label = locationLabel,
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
                if (type == StoreKind.REMOTE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .handPointer()
                            .clickable {
                                val enabled = !useSsh
                                useSsh = enabled
                                if (enabled) {
                                    val parsed = parseHttpUrl(directory)
                                    if (sshHost.isBlank()) {
                                        parsed.host?.let { sshHost = it }
                                    }
                                    if (sshPort.isBlank()) {
                                        sshPort = "22"
                                    }
                                }
                            },
                    ) {
                        Checkbox(
                            modifier = Modifier.handPointer(),
                            checked = useSsh,
                            onCheckedChange = null,
                        )
                        Text("Use SSH tunnel", style = MaterialTheme.typography.bodySmall)
                    }
                    if (useSsh) {
                        SmallOutlinedTextField(
                            value = sshHost,
                            onValueChange = { sshHost = it },
                            label = "SSH host",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SmallOutlinedTextField(
                            value = sshUser,
                            onValueChange = { sshUser = it },
                            label = "SSH user (optional)",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SmallOutlinedTextField(
                            value = sshPort,
                            onValueChange = { sshPort = it },
                            label = "SSH port (default 22)",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SmallOutlinedTextField(
                            value = sshLocalPort,
                            onValueChange = { sshLocalPort = it },
                            label = "Local port (optional)",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SmallOutlinedTextField(
                            value = sshIdentityFile,
                            onValueChange = { sshIdentityFile = it },
                            label = "Identity file (optional)",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ModalSecondaryButton(
                    label = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { storesState.closeStoreEditor() },
                )
                ModalPrimaryButton(
                    label = "Save",
                    icon = Icons.Default.Edit,
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    ),
                    onClick = {
                        val trimmedName = name.trim()
                        val trimmedDirectory = directory.trim()
                        val trimmedSshHost = sshHost.trim()
                        val trimmedSshUser = sshUser.trim()
                        val trimmedSshPort = sshPort.trim()
                        val trimmedSshLocalPort = sshLocalPort.trim()
                        val trimmedSshIdentityFile = sshIdentityFile.trim()
                        if (trimmedName.isBlank()) {
                            error = "Name is required."
                            return@ModalPrimaryButton
                        }
                        if (trimmedDirectory.isBlank()) {
                            error = if (type == StoreKind.REMOTE) "Remote URL is required." else "Directory is required."
                            return@ModalPrimaryButton
                        }
                        val resolvedSshPort = if (trimmedSshPort.isBlank()) null else trimmedSshPort.toIntOrNull()
                        if (type == StoreKind.REMOTE && useSsh && trimmedSshHost.isBlank()) {
                            error = "SSH host is required."
                            return@ModalPrimaryButton
                        }
                        if (type == StoreKind.REMOTE && useSsh && trimmedSshPort.isNotBlank() && resolvedSshPort == null) {
                            error = "SSH port must be a number."
                            return@ModalPrimaryButton
                        }
                        if (resolvedSshPort != null && resolvedSshPort !in 1..65535) {
                            error = "SSH port must be between 1 and 65535."
                            return@ModalPrimaryButton
                        }
                        val resolvedSshLocalPort = if (trimmedSshLocalPort.isBlank()) null else trimmedSshLocalPort.toIntOrNull()
                        if (type == StoreKind.REMOTE && useSsh && trimmedSshLocalPort.isNotBlank() && resolvedSshLocalPort == null) {
                            error = "Local port must be a number."
                            return@ModalPrimaryButton
                        }
                        if (resolvedSshLocalPort != null && resolvedSshLocalPort !in 1..65535) {
                            error = "Local port must be between 1 and 65535."
                            return@ModalPrimaryButton
                        }
                        val cluster = if (type == StoreKind.FOUNDATION_DB) clusterFile.trim().ifBlank { null } else null
                        val tenantValue = if (type == StoreKind.FOUNDATION_DB) tenant.trim().ifBlank { null } else null
                        val sshEnabled = type == StoreKind.REMOTE && useSsh
                        val sshHostValue = if (sshEnabled) trimmedSshHost.ifBlank { null } else null
                        val sshUserValue = if (sshEnabled) trimmedSshUser.ifBlank { null } else null
                        val sshIdentityValue = if (sshEnabled) trimmedSshIdentityFile.ifBlank { null } else null
                        val newStore = StoreDefinition(
                            id = editing?.id ?: generateStoreId(),
                            name = trimmedName,
                            type = type,
                            directory = trimmedDirectory,
                            clusterFile = cluster,
                            tenant = tenantValue,
                            sshHost = sshHostValue,
                            sshUser = sshUserValue,
                            sshPort = if (sshEnabled) resolvedSshPort else null,
                            sshLocalPort = if (sshEnabled) resolvedSshLocalPort else null,
                            sshIdentityFile = sshIdentityValue,
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
        StoreTypeTab(
            label = "Remote",
            selected = selected == StoreKind.REMOTE,
            onClick = { onSelected(StoreKind.REMOTE) },
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
    val background = if (selected) colors.secondary else Color.Transparent
    val textColor = if (selected) colors.onSecondary else colors.onSurface
    Box(
        modifier = modifier
            .height(26.dp)
            .background(background, shape)
            .handPointer().clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

private data class ParsedUrl(
    val host: String?,
    val port: Int?,
)

private fun parseHttpUrl(value: String): ParsedUrl {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return ParsedUrl(null, null)
    val withoutScheme = trimmed.substringAfter("://", trimmed)
    val authority = withoutScheme.substringBefore("/")
    if (authority.isBlank()) return ParsedUrl(null, null)
    val hostPort = authority.substringAfterLast("@")
    if (hostPort.startsWith("[")) {
        val end = hostPort.indexOf(']')
        if (end == -1) return ParsedUrl(hostPort, null)
        val host = hostPort.substring(1, end)
        val portPart = hostPort.substring(end + 1).removePrefix(":")
        val port = portPart.toIntOrNull()
        return ParsedUrl(host, port)
    }
    val parts = hostPort.split(":", limit = 2)
    val host = parts.firstOrNull()?.ifBlank { null }
    val port = parts.getOrNull(1)?.toIntOrNull()
    return ParsedUrl(host, port)
}

@Composable
private fun SmallOutlinedButton(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.handPointer(enabled),
        shape = RoundedCornerShape(4.dp),
        contentPadding = denseButtonPadding,
        border = BorderStroke(1.dp, tint),
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
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(denseIconButtonSize).handPointer(enabled),
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
        primary = Color(0xFFA7C2FF),
        secondary = Color(0xFF5A7BFF),
        tertiary = Color(0xFF8A7DFF),
        surface = Color(0xFF171F23),
        surfaceVariant = Color(0xFF232C31),
        background = Color(0xFF12181B),
        error = Color(0xFFFF7A7A),
    )
    val lightScheme = lightColorScheme(
        primary = Color(0xFF2E62FF),
        secondary = Color(0xFF4660F5),
        tertiary = Color(0xFF6C5CFF),
        surfaceVariant = Color(0xFFE7EDF2),
        surface = Color(0xFFFFFFFF),
        background = Color(0xFFF7FAFC),
        error = Color(0xFFD64545),
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
