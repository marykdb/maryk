package io.maryk.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

fun Modifier.handPointer(enabled: Boolean = true): Modifier =
    if (enabled) this.pointerHoverIcon(PointerIcon.Hand) else this
