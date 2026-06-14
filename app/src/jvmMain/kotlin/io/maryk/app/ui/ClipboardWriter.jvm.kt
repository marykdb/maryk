package io.maryk.app.ui

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import maryk.datastore.shared.runCatchingNonFatal

actual fun copyToClipboard(text: String) {
    runCatchingNonFatal {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
