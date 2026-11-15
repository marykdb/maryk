package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpResponseValidator
import java.net.http.HttpClient.Version.HTTP_1_1

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(Java) {
    // Reuse finite POST connections. CIO dedicates a fresh socket to every POST.
    engine { protocolVersion = HTTP_1_1 }
    HttpResponseValidator {
        handleResponseExceptionWithRequest { cause, _ ->
            // The JDK parses Content-Length before exposing response headers.
            if (cause is NumberFormatException) {
                throw IllegalStateException("Remote store response has invalid Content-Length header", cause)
            }
        }
    }
}
