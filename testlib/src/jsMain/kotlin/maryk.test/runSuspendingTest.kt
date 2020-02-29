package maryk.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

/**
 * Workaround to run suspending tests
 * https://youtrack.jetbrains.com/issue/KT-22228
 */
actual fun <A> runSuspendingTest(block: suspend CoroutineScope.() -> A): dynamic = GlobalScope.promise { block() }
