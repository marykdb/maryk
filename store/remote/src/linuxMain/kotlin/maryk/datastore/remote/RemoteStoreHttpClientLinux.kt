package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(CIO) {
    engine { requestTimeout = 0 }
}
