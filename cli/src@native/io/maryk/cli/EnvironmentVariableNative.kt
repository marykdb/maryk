@file:OptIn(ExperimentalForeignApi::class)

package io.maryk.cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

internal actual fun readEnvironmentVariable(name: String): String? = getenv(name)?.toKString()
