package io.maryk.app.ui.browser.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.maryk.app.ui.handPointer

internal fun buildEditorHeaderLabel(
    label: String,
    required: Boolean,
    countLabel: String?,
): String {
    return buildString {
        append(label)
        if (required) append(" *")
        if (!countLabel.isNullOrBlank()) append(" ($countLabel)")
    }
}

@Composable
internal fun EditorCollapsibleHeader(
    label: String,
    required: Boolean,
    indent: Int,
    expanded: Boolean,
    typeLabel: String,
    countLabel: String?,
    enabled: Boolean,
    allowUnset: Boolean,
    onUnset: (() -> Unit)?,
    onToggle: () -> Unit,
    onAdd: (() -> Unit)?,
) {
    val headerLabel = buildEditorHeaderLabel(label, required, countLabel)
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 12).dp, top = 2.dp)
            .offset(x = (-6).dp)
            .handPointer().clickable { onToggle() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(headerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onAdd != null) {
                OutlinedButton(
                    onClick = onAdd,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.height(22.dp).handPointer(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(12.dp))
                        Text(
                            "Add",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.offset(y = (-1).dp),
                        )
                    }
                }
            }
            if (allowUnset && onUnset != null) {
                IconButton(onClick = onUnset, modifier = Modifier.size(22.dp).handPointer()) {
                    Icon(Icons.Default.Delete, contentDescription = "Unset", modifier = Modifier.size(14.dp))
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun EditorTextRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    required: Boolean = false,
    enabled: Boolean,
    indent: Int = 0,
    placeholder: String? = null,
    error: String? = null,
    allowUnset: Boolean = false,
    onUnset: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    autoFocus: Boolean = false,
    isMultiline: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(label) {
            focusRequester.requestFocus()
        }
    }
    EditorRowShell(label = label, required = required, indent = indent, error = error) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                placeholder = placeholder,
                isError = error != null,
                focusRequester = focusRequester,
                trailingContent = trailingContent,
                isMultiline = isMultiline,
            )
            if (allowUnset && onUnset != null) {
                IconButton(onClick = onUnset, modifier = Modifier.size(22.dp).handPointer()) {
                    Icon(Icons.Default.Close, contentDescription = "Unset", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
internal fun EditorRowShell(
    label: String,
    required: Boolean,
    indent: Int,
    error: String?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = (indent * 12).dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val labelText = if (required) "$label *" else label
            Text(
                labelText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(140.dp).padding(top = 6.dp),
            )
            content()
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 148.dp),
            )
        }
    }
}

@Composable
internal fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String?,
    isError: Boolean,
    focusRequester: FocusRequester? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    isMultiline: Boolean = false,
) {
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val background = if (enabled) {
        Color.White
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = background,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .height(if (isMultiline) 72.dp else 30.dp)
            .widthIn(min = 180.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = if (isMultiline) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = !isMultiline,
                    maxLines = if (isMultiline) 4 else 1,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.SansSerif),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = (if (isMultiline) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).let { base ->
                        if (focusRequester != null) base.focusRequester(focusRequester) else base
                    },
                )
                if (value.isBlank() && placeholder != null) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
            if (trailingContent != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    trailingContent()
                }
            }
        }
    }
}
