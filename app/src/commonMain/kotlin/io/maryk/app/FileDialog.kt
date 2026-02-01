package io.maryk.app

expect fun pickDirectory(title: String): String?
expect fun pickFile(title: String, extensions: List<String> = emptyList()): String?