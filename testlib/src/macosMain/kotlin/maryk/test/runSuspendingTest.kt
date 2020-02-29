package maryk.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

actual fun <A> runSuspendingTest(block: suspend CoroutineScope.() -> A) = runBlocking { block() }
