package io.maryk.cli

import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.isatty

actual fun isInteractiveTerminal(): Boolean {
    val stdinInteractive = isatty(STDIN_FILENO) != 0
    val stdoutInteractive = isatty(STDOUT_FILENO) != 0
    return stdinInteractive && stdoutInteractive
}
