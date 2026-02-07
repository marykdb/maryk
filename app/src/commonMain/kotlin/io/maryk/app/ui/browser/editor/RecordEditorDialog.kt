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

internal enum class RecordEditorMode {
    ADD,
    EDIT,
}

internal fun isSaveFailureMessage(message: String): Boolean {
    return message.contains("failed", ignoreCase = true)
        || message.contains("time travel mode active", ignoreCase = true)
}

@Composable
internal fun RecordEditorDialog(
    state: BrowserState,
    mode: RecordEditorMode,
    dataModel: IsRootDataModel,
    initialValues: Values<IsRootDataModel>,
    initialKeyText: String?,
    onDismiss: () -> Unit,
) {
    val editorState = remember(initialValues, dataModel, mode) {
        RecordEditState(dataModel, initialValues, allowFinalEdit = mode == RecordEditorMode.ADD)
    }
    var keyText by remember(initialKeyText, mode) { mutableStateOf(initialKeyText.orEmpty()) }
    var keyError by remember(mode, initialKeyText) { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var awaitingSave by remember { mutableStateOf(false) }
    val lastMessage = state.lastActionMessage
    val canEdit = !state.isWorking
    val hasErrors = editorState.hasErrors || keyError != null
    val hasChanges = mode == RecordEditorMode.ADD || editorState.hasChanges

    if (awaitingSave && !state.isWorking && lastMessage != null) {
        if (isSaveFailureMessage(lastMessage)) {
            saveError = lastMessage
            awaitingSave = false
        } else {
            awaitingSave = false
            onDismiss()
        }
    }

    ModalSurface(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (mode == RecordEditorMode.ADD) "Add record" else "Edit record",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        dataModel.Meta.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp).handPointer()) {
                    Icon(Icons.Default.Close, contentDescription = "Close editor", modifier = Modifier.size(16.dp))
                }
            }

            if (mode == RecordEditorMode.ADD) {
                EditorTextRow(
                    label = "Key (optional)",
                    value = keyText,
                    onValueChange = {
                        keyText = it
                        keyError = null
                    },
                    placeholder = "Leave empty for auto key",
                    enabled = true,
                    error = keyError,
                )
            } else if (!initialKeyText.isNullOrBlank()) {
                EditorTextRow(
                    label = "Key",
                    value = initialKeyText,
                    onValueChange = {},
                    enabled = false,
                )
            }

            saveError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            val scrollState = rememberScrollState()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dataModel.forEach { wrapper ->
                            EditorField(
                                editorState = editorState,
                                state = state,
                                label = wrapper.name,
                                wrapper = wrapper,
                                parentRef = null,
                                indent = 0,
                                errorProvider = editorState::errorFor,
                                onError = editorState::setError,
                            )
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModalSecondaryButton(
                    label = "Cancel",
                    onClick = onDismiss,
                )
                ModalPrimaryButton(
                    label = "Save",
                    icon = Icons.Default.Edit,
                    onClick = {
                        saveError = null
                        if (!editorState.validateAll()) {
                            saveError = "Fix validation errors before saving."
                            return@ModalPrimaryButton
                        }
                        if (state.timeTravelEnabled) {
                            saveError = "Time travel mode active."
                            return@ModalPrimaryButton
                        }
                        if (mode == RecordEditorMode.ADD) {
                            val key = keyText.trim().takeIf { it.isNotBlank() }?.let {
                                runCatching { dataModel.key(it) }.getOrNull()
                            }
                            if (keyText.isNotBlank() && key == null) {
                                keyError = "Invalid key format."
                                return@ModalPrimaryButton
                            }
                            awaitingSave = true
                            state.addRecord(editorState.serializableValues(), key)
                        } else {
                            if (!editorState.hasChanges) {
                                saveError = "No changes to save."
                                return@ModalPrimaryButton
                            }
                            val changes = editorState.buildChanges()
                            if (changes.isEmpty()) {
                                saveError = "No changes to save."
                                return@ModalPrimaryButton
                            }
                            awaitingSave = true
                            state.applyRecordChanges(changes)
                        }
                    },
                    enabled = canEdit && hasChanges && !hasErrors,
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    ),
                )
            }
        }
    }
}
