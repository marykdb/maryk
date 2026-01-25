package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

fun createDefaultHttpClient(): HttpClient =
    HttpClient(CIO) {
        engine {
            requestTimeout = 0
        }
    }
