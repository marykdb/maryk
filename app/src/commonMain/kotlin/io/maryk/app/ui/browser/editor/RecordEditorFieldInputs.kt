package io.maryk.app.ui.browser.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.maryk.app.data.KEY_ORDER_TOKEN
import io.maryk.app.data.ScanQueryParser
import io.maryk.app.data.buildRequestContext
import io.maryk.app.data.buildSummary
import io.maryk.app.data.serializeRecordToYaml
import io.maryk.app.state.BrowserState
import io.maryk.app.state.BrowserUiState
import io.maryk.app.state.RecordDetails
import io.maryk.app.state.ScanRow
import io.maryk.app.ui.ModalPrimaryButton
import io.maryk.app.ui.ModalSecondaryButton
import io.maryk.app.ui.ModalSurface
import io.maryk.app.ui.handPointer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toLocalDateTime
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.models.IsValueDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.TypedObjectDataModel
import maryk.core.models.TypedValuesDataModel
import maryk.core.models.ValuesCollectorContext
import maryk.core.models.asValues
import maryk.core.models.emptyValues
import maryk.core.models.key
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsReferenceDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.ReferenceToMax
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.IsReferenceValueOrNullPair
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItem
import maryk.core.values.Values
import kotlin.time.Instant
@Composable
internal fun EditorValueField(
    label: String,
    path: String,
    definition: IsPropertyDefinition<*>,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    state: BrowserState,
    error: String?,
    autoFocus: Boolean = false,
    errorProvider: (String) -> String?,
    onPathError: (String, String?) -> Unit,
    onValueChange: (Any?) -> Unit,
    onError: (String?) -> Unit,
) {
    when (definition) {
        is EnumDefinition<*> -> {
            EnumEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                onValueChange = {
                    onValueChange(it)
                    onError(validateValue(definition, it, required))
                },
            )
        }
        is BooleanDefinition -> {
            BooleanEditor(
                label = label,
                path = path,
                value = value as? Boolean,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                onValueChange = {
                    onValueChange(it)
                    onError(validateValue(definition, it, required))
                },
            )
        }
        is DateDefinition -> {
            DateEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                autoFocus = autoFocus,
                onValueChange = onValueChange,
                onError = onError,
            )
        }
        is TimeDefinition -> {
            SimpleTextEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                placeholder = "HH:MM:SS",
                onValueChange = onValueChange,
                onError = onError,
            )
        }
        is DateTimeDefinition -> {
            DateTimeEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                autoFocus = autoFocus,
                onValueChange = onValueChange,
                onError = onError,
            )
        }
        is ValueObjectDefinition<*, *> -> {
            EditorValueObjectField(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                state = state,
                error = error,
                onValueChange = onValueChange,
                allowFinalEdit = true,
                errorProvider = errorProvider,
                onError = onPathError,
            )
        }
        is NumberDefinition<*> -> {
            SimpleTextEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                placeholder = "Number",
                autoFocus = autoFocus,
                onValueChange = onValueChange,
                onError = onError,
            )
        }
        else -> {
            SimpleTextEditor(
                label = label,
                path = path,
                definition = definition,
                value = value,
                enabled = enabled,
                required = required,
                indent = indent,
                error = error,
                placeholder = null,
                autoFocus = autoFocus,
                onValueChange = onValueChange,
                onError = onError,
            )
        }
    }
}

private const val longStringMaxSizeThreshold = 500

internal fun shouldUseMultilineTextEditor(
    label: String,
    definition: IsPropertyDefinition<*>,
): Boolean {
    return (definition as? StringDefinition)?.let { stringDefinition ->
        val isKeyLabel = label.startsWith("Key", ignoreCase = true)
        val maxSize = stringDefinition.maxSize?.toInt()
        !isKeyLabel && maxSize != null && maxSize >= longStringMaxSizeThreshold
    } ?: false
}

@Composable
private fun SimpleTextEditor(
    label: String,
    path: String,
    definition: IsPropertyDefinition<*>,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    error: String?,
    placeholder: String?,
    autoFocus: Boolean = false,
    onValueChange: (Any?) -> Unit,
    onError: (String?) -> Unit,
) {
    val isLongText = shouldUseMultilineTextEditor(label, definition)
    val displayValue = value?.let { formatValue(definition, it) }.orEmpty()
    var text by remember(path, displayValue) { mutableStateOf(displayValue) }

    EditorTextRow(
        label = label,
        required = required,
        value = text,
        onValueChange = { next ->
            text = next
            if (next.isBlank()) {
                onValueChange(null)
                onError(if (required) "Required." else null)
                return@EditorTextRow
            }
            val parsed = parseValue(definition, next)
            if (parsed is ParseResult.Error) {
                onError(parsed.message)
            } else if (parsed is ParseResult.Value) {
                onValueChange(parsed.value)
                onError(validateValue(definition, parsed.value, required))
            }
        },
        enabled = enabled,
        indent = indent,
        placeholder = placeholder,
        error = error,
        autoFocus = autoFocus,
        isMultiline = isLongText,
        allowUnset = !required && enabled,
        onUnset = {
            text = ""
            onValueChange(null)
            onError(null)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateEditor(
    label: String,
    path: String,
    definition: DateDefinition,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    error: String?,
    autoFocus: Boolean = false,
    onValueChange: (Any?) -> Unit,
    onError: (String?) -> Unit,
) {
    val displayValue = value?.let { formatValue(definition, it) }.orEmpty()
    var text by remember(path, displayValue) { mutableStateOf(displayValue) }
    val initialMillis = (value as? LocalDate)
        ?.atStartOfDayIn(TimeZone.UTC)
        ?.toEpochMilliseconds()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    var showPicker by remember(path) { mutableStateOf(false) }

    EditorTextRow(
        label = label,
        required = required,
        value = text,
        onValueChange = { next ->
            text = next
            if (next.isBlank()) {
                onValueChange(null)
                onError(if (required) "Required." else null)
                return@EditorTextRow
            }
            val parsed = parseValue(definition, next)
            if (parsed is ParseResult.Error) {
                onError(parsed.message)
            } else if (parsed is ParseResult.Value) {
                onValueChange(parsed.value)
                val parsedDate = parsed.value as? LocalDate
                if (parsedDate != null) {
                    datePickerState.selectedDateMillis =
                        parsedDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                }
                onError(validateValue(definition, parsed.value, required))
            }
        },
        enabled = enabled,
        indent = indent,
        placeholder = "YYYY-MM-DD",
        error = error,
        autoFocus = autoFocus,
        allowUnset = !required && enabled,
        onUnset = {
            text = ""
            onValueChange(null)
            onError(null)
        },
        trailingContent = {
            IconButton(
                onClick = { showPicker = true },
                enabled = enabled,
                modifier = Modifier.size(24.dp).handPointer(),
            ) {
                Icon(Icons.Default.DateRange, contentDescription = "Pick date", modifier = Modifier.size(16.dp))
            }
        },
    )
    val selectedMillis = datePickerState.selectedDateMillis
    if (selectedMillis != null) {
        LaunchedEffect(selectedMillis) {
            val date = Instant.fromEpochMilliseconds(selectedMillis)
                .toLocalDateTime(TimeZone.UTC)
                .date
            val formatted = formatValue(definition, date)
            if (formatted != text) {
                text = formatted
            }
            onValueChange(date)
            onError(validateValue(definition, date, required))
        }
    }
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                ModalPrimaryButton(label = "Done", onClick = { showPicker = false })
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
private fun DateTimeEditor(
    label: String,
    path: String,
    definition: DateTimeDefinition,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    error: String?,
    autoFocus: Boolean = false,
    onValueChange: (Any?) -> Unit,
    onError: (String?) -> Unit,
) {
    val displayValue = value?.let { formatValue(definition, it) }.orEmpty()
    var text by remember(path, displayValue) { mutableStateOf(displayValue) }
    val initialDateTime = value as? LocalDateTime
    val initialMillis = initialDateTime
        ?.date
        ?.atStartOfDayIn(TimeZone.UTC)
        ?.toEpochMilliseconds()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    val timePickerState = rememberTimePickerState(
        initialHour = initialDateTime?.hour ?: 0,
        initialMinute = initialDateTime?.minute ?: 0,
        is24Hour = true,
    )
    var showPicker by remember(path) { mutableStateOf(false) }

    EditorTextRow(
        label = label,
        required = required,
        value = text,
        onValueChange = { next ->
            text = next
            if (next.isBlank()) {
                onValueChange(null)
                onError(if (required) "Required." else null)
                return@EditorTextRow
            }
            val parsed = parseValue(definition, next)
            if (parsed is ParseResult.Error) {
                onError(parsed.message)
            } else if (parsed is ParseResult.Value) {
                onValueChange(parsed.value)
                val parsedDateTime = parsed.value as? LocalDateTime
                if (parsedDateTime != null) {
                    datePickerState.selectedDateMillis =
                        parsedDateTime.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                    timePickerState.hour = parsedDateTime.hour
                    timePickerState.minute = parsedDateTime.minute
                }
                onError(validateValue(definition, parsed.value, required))
            }
        },
        enabled = enabled,
        indent = indent,
        placeholder = "YYYY-MM-DDTHH:MM:SS",
        error = error,
        autoFocus = autoFocus,
        allowUnset = !required && enabled,
        onUnset = {
            text = ""
            onValueChange(null)
            onError(null)
        },
        trailingContent = {
            IconButton(
                onClick = { showPicker = true },
                enabled = enabled,
                modifier = Modifier.size(24.dp).handPointer(),
            ) {
                Icon(Icons.Default.DateRange, contentDescription = "Pick date/time", modifier = Modifier.size(16.dp))
            }
        },
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                ModalPrimaryButton(label = "Done", onClick = { showPicker = false })
            },
            dismissButton = {
                ModalSecondaryButton(label = "Cancel", onClick = { showPicker = false })
            },
        ) {
            Column(
                modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DatePicker(state = datePickerState, showModeToggle = false)
                TimePicker(state = timePickerState)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    val selectedMillis = datePickerState.selectedDateMillis
    if (selectedMillis != null) {
        LaunchedEffect(selectedMillis, timePickerState.hour, timePickerState.minute) {
            val date = Instant.fromEpochMilliseconds(selectedMillis)
                .toLocalDateTime(TimeZone.UTC)
                .date
            val dateTime = mergeDateTimeSelection(
                existing = value as? LocalDateTime,
                selectedDate = date,
                selectedHour = timePickerState.hour,
                selectedMinute = timePickerState.minute,
            )
            val formatted = formatValue(definition, dateTime)
            if (formatted != text) {
                text = formatted
            }
            onValueChange(dateTime)
            onError(validateValue(definition, dateTime, required))
        }
    }
}

@Composable
private fun EnumEditor(
    label: String,
    path: String,
    definition: EnumDefinition<*>,
    value: Any?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    error: String?,
    onValueChange: (Any?) -> Unit,
) {
    var expanded by remember(path) { mutableStateOf(false) }
    @Suppress("UNCHECKED_CAST")
    val enumDefinition = definition.enum as IndexedEnumDefinition<IndexedEnumComparable<Any>>
    val cases = enumDefinition.cases()
    val currentLabel = cases.firstOrNull { it == value }?.let { enumDefinition.asString(it) }.orEmpty()
    EditorRowShell(label = label, required = required, indent = indent, error = error) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (enabled) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.height(30.dp).widthIn(min = 120.dp).handPointer().clickable(enabled = enabled) { expanded = true },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (currentLabel.isBlank()) "Select…" else currentLabel,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    cases.forEach { case ->
                        DropdownMenuItem(
                            text = { Text(enumDefinition.asString(case), style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                expanded = false
                                onValueChange(case)
                            },
                            enabled = enabled,
                        )
                    }
                }
            }
            if (!required && enabled) {
                IconButton(onClick = { onValueChange(null) }, modifier = Modifier.size(24.dp).handPointer()) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun BooleanEditor(
    label: String,
    path: String,
    value: Boolean?,
    enabled: Boolean,
    required: Boolean,
    indent: Int,
    error: String?,
    onValueChange: (Boolean?) -> Unit,
) {
    var expanded by remember(path) { mutableStateOf(false) }
    val labelText = when (value) {
        null -> if (required) "Select…" else "Unset"
        true -> "True"
        false -> "False"
    }
    EditorRowShell(label = label, required = required, indent = indent, error = error) {
        Box {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (enabled) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.height(30.dp).widthIn(min = 120.dp).handPointer().clickable(enabled = enabled) { expanded = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(labelText, style = MaterialTheme.typography.bodySmall)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("True") }, onClick = { expanded = false; onValueChange(true) }, enabled = enabled)
                DropdownMenuItem(text = { Text("False") }, onClick = { expanded = false; onValueChange(false) }, enabled = enabled)
                if (!required) {
                    DropdownMenuItem(text = { Text("Unset") }, onClick = { expanded = false; onValueChange(null) }, enabled = enabled)
                }
            }
        }
    }
}
