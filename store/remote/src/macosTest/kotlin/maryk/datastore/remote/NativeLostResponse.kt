package maryk.datastore.remote

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeStringUtf8
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class NativeLostResponse {
    @Test
    fun darwinDoesNotReplayPostWhenResponseIsLost() = runBlocking {
        val requests = atomic(0)
        val connections = atomic(0)
        val selector = SelectorManager(Dispatchers.Default)
        val listener = aSocket(selector).tcp().bind("127.0.0.1", 0)
        val server = launch {
            var warmed = false
            while (true) {
                val socket = listener.accept()
                connections.incrementAndGet()
                try {
                    val input = socket.openReadChannel()
                    val output = socket.openWriteChannel(autoFlush = true)
                    while (true) {
                        val request = input.readLine() ?: break
                        var length = 0
                        while (true) {
                            val line = input.readLine() ?: error("Missing request headers")
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toInt()
                        }
                        input.readFully(ByteArray(length), 0, length)
                        if (!warmed) {
                            check(request.startsWith("POST /warm "))
                            warmed = true
                            output.writeStringUtf8("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok")
                        } else {
                            requests.incrementAndGet()
                            break
                        }
                    }
                } finally { socket.close() }
            }
        }
        val client = createDefaultHttpClient()
        try {
            val url = "http://127.0.0.1:${(listener.localAddress as InetSocketAddress).port}"
            withTimeout(10_000) {
                assertEquals("ok", client.post("$url/warm") { setBody("warm") }.bodyAsText())
                assertFails { client.post("$url/mutate") { setBody("write") } }
                assertEquals(1, requests.value)
                assertEquals(1, connections.value)
            }
        } finally {
            client.close()
            server.cancelAndJoin()
            listener.close()
            selector.close()
        }
    }
}
