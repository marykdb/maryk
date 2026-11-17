package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlin.time.Duration.Companion.days

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        configureSession {
            // Maryk bounds finite calls and can monitor flow heartbeats independently.
            timeoutIntervalForRequest = 1.days.inWholeSeconds.toDouble()
            timeoutIntervalForResource = 7.days.inWholeSeconds.toDouble()
            URLCache = null
        }
        configureRequest {
            setTimeoutInterval(1.days.inWholeSeconds.toDouble())
        }
    }
}
