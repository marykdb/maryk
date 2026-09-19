package io.maryk.cli

internal actual fun readEnvironmentVariable(name: String): String? = System.getenv(name)
