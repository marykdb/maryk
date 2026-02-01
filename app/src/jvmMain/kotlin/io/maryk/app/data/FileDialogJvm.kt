package io.maryk.app.data

import java.awt.FileDialog
import java.awt.Frame
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual fun pickDirectory(title: String): String? {
    val osName = System.getProperty("os.name")?.lowercase() ?: ""
    if (osName.contains("mac")) {
        val previous = System.getProperty("apple.awt.fileDialogForDirectories")
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isVisible = true
        val directory = dialog.directory
        val file = dialog.file
        dialog.dispose()
        if (previous == null) {
            System.clearProperty("apple.awt.fileDialogForDirectories")
        } else {
            System.setProperty("apple.awt.fileDialogForDirectories", previous)
        }
        if (directory == null || file == null) return null
        return if (directory.endsWith("/") || directory.endsWith("\\")) {
            directory + file
        } else {
            "$directory/$file"
        }
    }

    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile?.absolutePath
}

actual fun pickFile(title: String, extensions: List<String>): String? {
    val osName = System.getProperty("os.name")?.lowercase() ?: ""
    val normalized = extensions.map { it.trim().trimStart('.').lowercase() }.filter { it.isNotEmpty() }
    if (osName.contains("mac")) {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        if (normalized.isNotEmpty()) {
            dialog.setFilenameFilter { _, name ->
                normalized.any { name.lowercase().endsWith(".$it") }
            }
        }
        dialog.isVisible = true
        val directory = dialog.directory
        val file = dialog.file
        dialog.dispose()
        if (directory == null || file == null) return null
        return if (directory.endsWith("/") || directory.endsWith("\\")) {
            directory + file
        } else {
            "$directory/$file"
        }
    }

    val chooser = JFileChooser().apply {
        dialogTitle = title
        if (normalized.isNotEmpty()) {
            fileFilter = FileNameExtensionFilter(
                normalized.joinToString(", ") { ".$it" },
                *normalized.toTypedArray(),
            )
        }
        isAcceptAllFileFilterUsed = true
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile?.absolutePath
}