package maryk.datastore.remote

import io.ktor.client.HttpClient

fun createDefaultHttpClient(): HttpClient = createPlatformHttpClient()

internal expect fun createPlatformHttpClient(): HttpClient
