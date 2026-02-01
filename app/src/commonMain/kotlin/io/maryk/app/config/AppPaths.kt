package io.maryk.app.config

expect fun storesFilePath(): String

expect fun ensureParentDirectory(path: String)