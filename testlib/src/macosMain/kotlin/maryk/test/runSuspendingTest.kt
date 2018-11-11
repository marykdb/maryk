package maryk.test

import kotlinx.coroutines.runBlocking

actual fun runSuspendingTest(block: suspend () -> Unit) = runBlocking { block() }
