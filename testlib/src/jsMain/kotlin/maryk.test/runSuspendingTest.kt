package maryk.test

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

/**
 * Workaround to run suspending tests
 * https://youtrack.jetbrains.com/issue/KT-22228
 */
actual fun runSuspendingTest(block: suspend () -> Unit): dynamic = GlobalScope.promise { block() }
