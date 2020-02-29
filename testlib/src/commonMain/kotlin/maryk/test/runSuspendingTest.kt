package maryk.test

import kotlinx.coroutines.CoroutineScope

expect fun <A> runSuspendingTest(block: suspend CoroutineScope.() -> A): A
