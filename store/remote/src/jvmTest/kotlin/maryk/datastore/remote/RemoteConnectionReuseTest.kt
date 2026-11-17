package maryk.datastore.remote

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.response.header
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeInt
import java.io.IOException
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class RemoteConnectionReuseTest {
    @Test
    fun retryAfterResponseDoesNotReplayAMutation() = runBoundedIntegrationTest {
        val requests = AtomicInteger()
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                post("/mutation") {
                    call.receiveText()
                    requests.incrementAndGet()
                    call.response.header(HttpHeaders.RetryAfter, "0")
                    call.respondText("Uncertain outcome", status = HttpStatusCode.ServiceUnavailable)
                }
            }
        }.start(wait = false)
        val client = createDefaultHttpClient()
        try {
            val port = server.engine.resolvedConnectors().single().port
            val response = client.post("http://127.0.0.1:$port/mutation") { setBody("mutation") }
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals(1, requests.get())
        } finally {
            client.close()
            server.stop(0, 1_000)
        }
    }

    @Test
    fun streamingResponseCanBeIdleBeyondTheEngineDefaultSocketTimeout() = runBoundedIntegrationTest {
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                get("/idle") {
                    call.respondBytesWriter {
                        writeInt(1)
                        flush()
                        delay(11_000)
                        writeInt(2)
                    }
                }
            }
        }.start(wait = false)
        val client = createDefaultHttpClient()
        try {
            val port = server.engine.resolvedConnectors().single().port
            withTimeout(20_000) {
                client.prepareGet("http://127.0.0.1:$port/idle").execute { response ->
                    val channel = response.bodyAsChannel()
                    assertEquals(1, channel.readInt())
                    assertEquals(2, channel.readInt())
                }
            }
        } finally {
            client.close()
            server.stop(0, 1_000)
        }
    }

    @Test
    fun abandoningAStreamingResponseDoesNotWaitForTheRemainingBody() = runBoundedIntegrationTest {
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                get("/stream") {
                    call.respondBytesWriter {
                        writeInt(42)
                        flush()
                        awaitCancellation()
                    }
                }
                get("/ready") { call.respondText("ready") }
            }
        }.start(wait = false)
        val client = createDefaultHttpClient()
        try {
            val url = "http://127.0.0.1:${server.engine.resolvedConnectors().single().port}"
            withTimeout(5_000) {
                client.prepareGet("$url/stream").execute { response ->
                    assertEquals(42, response.bodyAsChannel().readInt())
                }
                assertEquals("ready", client.get("$url/ready").bodyAsText())
            }
        } finally {
            client.close()
            server.stop(0, 1_000)
        }
    }

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
