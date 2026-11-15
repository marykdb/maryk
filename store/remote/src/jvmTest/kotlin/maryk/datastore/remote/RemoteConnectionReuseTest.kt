package maryk.datastore.remote

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.io.IOException
import java.net.ServerSocket
import java.net.SocketException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemoteConnectionReuseTest {
    @Test
    fun lostPostResponseDoesNotReplayTheMutation() = runBoundedIntegrationTest {
        val listener = ServerSocket(0)
        val received = AtomicInteger()
        val server = launch(Dispatchers.IO) {
            while (!listener.isClosed) {
                val socket = try { listener.accept() } catch (error: SocketException) {
                    if (listener.isClosed) break else throw error
                }
                socket.use {
                    it.soTimeout = 5_000
                    val reader = it.getInputStream().bufferedReader()
                    var length = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toInt()
                    }
                    repeat(length) { check(reader.read() >= 0) }
                    received.incrementAndGet()
                    // A mutation may have committed before its response was lost.
                }
            }
        }
        val client = createDefaultHttpClient()
        try {
            withTimeout(5_000) {
                assertFailsWith<IOException> {
                    client.post("http://127.0.0.1:${listener.localPort}/rpc") {
                        setBody("mutation".encodeToByteArray())
                    }.bodyAsText()
                }
            }
            assertEquals(1, received.get())
        } finally {
            client.close()
            listener.close()
            server.cancelAndJoin()
        }
    }

    @Test
    fun finitePostsReuseConnectionsWithoutReplayingMutations() = runBoundedIntegrationTest {
        val ports = ConcurrentHashMap.newKeySet<Int>()
        val requests = AtomicInteger()
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                post("/rpc") {
                    requests.incrementAndGet()
                    ports += call.request.local.remotePort
                    call.respondText(call.receiveText())
                }
            }
        }.start(wait = false)
        val client = createDefaultHttpClient()
        try {
            val port = server.engine.resolvedConnectors().single().port
            repeat(256) { index ->
                assertEquals("mutation-$index", client.post("http://127.0.0.1:$port/rpc") {
                    setBody("mutation-$index".encodeToByteArray())
                }.bodyAsText())
            }
            assertTrue(ports.size <= 4, "Expected connection reuse, observed ${ports.size} client ports")
            assertEquals(256, requests.get())
        } finally {
            client.close()
            server.stop(0, 1_000)
        }
    }
}
