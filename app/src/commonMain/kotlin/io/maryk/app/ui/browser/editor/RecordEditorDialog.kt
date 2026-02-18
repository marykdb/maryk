package io.maryk.app.ui.browser.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import io.maryk.app.state.BrowserState
import io.maryk.app.ui.ModalPrimaryButton
import io.maryk.app.ui.ModalSecondaryButton
import io.maryk.app.ui.ModalSurface
import io.maryk.app.ui.handPointer
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.values.Values

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
