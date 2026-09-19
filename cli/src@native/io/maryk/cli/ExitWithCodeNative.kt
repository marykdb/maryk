package io.maryk.cli

import kotlin.system.exitProcess

internal actual fun exitWithCode(status: Int): Nothing = exitProcess(status)
