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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant

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
                    IconButton(onClick = onToggleCatalog, modifier = Modifier.size(32.dp).handPointer()) {
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
                    IconButton(onClick = { state.toggleTimeTravel() }, modifier = Modifier.size(32.dp).handPointer()) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Toggle time travel",
                            tint = if (state.timeTravelEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onToggleInspector, modifier = Modifier.size(32.dp).handPointer()) {
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
                if (state.timeTravelEnabled) {
                    TimeTravelBar(
                        dateValue = state.timeTravelDate,
                        timeValue = state.timeTravelTime,
                        onDateChange = state::updateTimeTravelDate,
                        onTimeChange = state::updateTimeTravelTime,
                        onClose = { state.updateTimeTravelEnabled(false) },
                    )
                }
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
                modifier = Modifier.size(20.dp).handPointer(),
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
private fun TimeTravelBar(
    dateValue: String,
    timeValue: String,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "Time travel",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Time travel",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.align(Alignment.CenterVertically).offset(y = (-1.5).dp),
            )
            TimeTravelDateField(
                value = dateValue,
                onValueChange = onDateChange,
                modifier = Modifier.width(140.dp),
            )
            TimeTravelTimeField(
                value = timeValue,
                onValueChange = onTimeChange,
                modifier = Modifier.width(90.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp).handPointer()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close time travel",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun TimeTravelField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    trailingContent: @Composable (() -> Unit)?,
    isValid: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    val colors = MaterialTheme.colorScheme
    val textColor = if (isValid || value.isBlank()) colors.onSurface else colors.error
    val hintColor = if (isValid || value.isBlank()) colors.onSurfaceVariant else colors.error
    val borderColor = if (isValid || value.isBlank()) colors.outline.copy(alpha = 0.35f) else colors.error
    Surface(
        shape = shape,
        color = colors.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelSmall.copy(color = textColor),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (value.isBlank()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.labelSmall,
                        color = hintColor,
                    )
                }
            }
            trailingContent?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeTravelDateField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsedDate = remember(value) { parseTimeTravelDate(value) }
    val isValid = remember(value, parsedDate) { value.isBlank() || parsedDate != null }
    val initialMillis = parsedDate
        ?.atStartOfDayIn(TimeZone.currentSystemDefault())
        ?.toEpochMilliseconds()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(parsedDate) {
        if (parsedDate != null) {
            datePickerState.selectedDateMillis =
                parsedDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        }
    }

    TimeTravelField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "YYYY-MM-DD",
        trailingContent = {
            IconButton(onClick = { showPicker = true }, modifier = Modifier.size(22.dp).handPointer()) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Pick date",
                    modifier = Modifier.size(14.dp),
                )
            }
        },
        isValid = isValid,
        modifier = modifier,
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                ModalPrimaryButton(
                    label = "Done",
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val selectedDate = Instant.fromEpochMilliseconds(selectedMillis)
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                                .date
                            onValueChange(formatTimeTravelDate(selectedDate))
                        }
                        showPicker = false
                    },
                )
            },
            dismissButton = {
                ModalSecondaryButton(label = "Cancel", onClick = { showPicker = false })
            },
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeTravelTimeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsedTime = remember(value) { parseTimeTravelTime(value) }
    val isValid = remember(value, parsedTime) { value.isBlank() || parsedTime != null }
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
    val timePickerState = rememberTimePickerState(
        initialHour = parsedTime?.hour ?: now.hour,
        initialMinute = parsedTime?.minute ?: now.minute,
        is24Hour = true,
    )
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(parsedTime) {
        if (parsedTime != null) {
            timePickerState.hour = parsedTime.hour
            timePickerState.minute = parsedTime.minute
        }
    }

    TimeTravelField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "HH:MM",
        trailingContent = {
            IconButton(onClick = { showPicker = true }, modifier = Modifier.size(22.dp).handPointer()) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Pick time",
                    modifier = Modifier.size(14.dp),
                )
            }
        },
        isValid = isValid,
        modifier = modifier,
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                ModalPrimaryButton(
                    label = "Done",
                    onClick = {
                        onValueChange(formatTimeTravelTime(timePickerState.hour, timePickerState.minute))
                        showPicker = false
                    },
                )
            },
            dismissButton = {
                ModalSecondaryButton(label = "Cancel", onClick = { showPicker = false })
            },
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.widthIn(min = 220.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimeInput(state = timePickerState)
                }
            }
        }
    }
}

private fun parseTimeTravelDate(value: String): LocalDate? {
    val parts = value.trim().split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    return runCatching { LocalDate(year, month, day) }.getOrNull()
}

private fun parseTimeTravelTime(value: String): LocalTime? {
    val parts = value.trim().split(":")
    if (parts.size < 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
    if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
    return runCatching { LocalTime(hour, minute, second) }.getOrNull()
}

private fun formatTimeTravelDate(date: LocalDate): String {
    return "%04d-%02d-%02d".format(date.year, date.month.ordinal + 1, date.day)
}

private fun formatTimeTravelTime(hour: Int, minute: Int): String {
    return "%02d:%02d".format(hour, minute)
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
            IconButton(modifier = Modifier.handPointer(), onClick = onDismiss) {
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
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp).handPointer()) {
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
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp).handPointer()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}