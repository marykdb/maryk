package maryk.test

import kotlinx.coroutines.runBlocking

actual fun <A> runSuspendingTest(block: suspend () -> A) = runBlocking { block() }
