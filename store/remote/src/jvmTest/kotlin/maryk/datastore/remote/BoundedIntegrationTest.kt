package maryk.datastore.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

private val integrationTestTimeout = 3.minutes

internal fun <T> runBoundedIntegrationTest(block: suspend CoroutineScope.() -> T): T = runBlocking {
    withTimeout(integrationTestTimeout) {
        block()
    }
}
