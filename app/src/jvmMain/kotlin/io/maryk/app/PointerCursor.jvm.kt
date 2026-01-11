package io.maryk.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor

actual fun Modifier.horizontalResizeCursor(): Modifier {
    val cursor = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
    return this.pointerHoverIcon(cursor)
}
