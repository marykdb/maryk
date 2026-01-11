package io.maryk.app

expect fun storesFilePath(): String

expect fun ensureParentDirectory(path: String)
