package io.maryk.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size

private val modalButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
private val modalButtonShape = RoundedCornerShape(4.dp)

@Composable
fun ModalPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    colors: ButtonColors? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.handPointer(enabled),
        shape = modalButtonShape,
        colors = colors ?: ButtonDefaults.buttonColors(),
        contentPadding = modalButtonPadding,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ModalSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.handPointer(enabled),
        shape = modalButtonShape,
        contentPadding = modalButtonPadding,
        border = BorderStroke(1.dp, tint),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
