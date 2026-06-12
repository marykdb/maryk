package io.maryk.app.ui

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import maryk.datastore.shared.rethrowIfFatal

actual fun copyToClipboard(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }.onFailure { it.rethrowIfFatal() }
}
