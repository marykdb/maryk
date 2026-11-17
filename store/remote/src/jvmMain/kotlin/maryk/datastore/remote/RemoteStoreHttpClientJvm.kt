package maryk.datastore.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit.MILLISECONDS
import okhttp3.Protocol.HTTP_1_1
import okhttp3.RequestBody
import okio.BufferedSink

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            protocols(listOf(HTTP_1_1))
            // Streams may be idle longer than OkHttp's default ten-second socket timeout.
            // Finite requests and flow heartbeats have their own coroutine timeouts.
            readTimeout(0, MILLISECONDS)
            writeTimeout(0, MILLISECONDS)
            // A lost response can hide a committed mutation. Only the caller may retry it.
            retryOnConnectionFailure(false)
        }
        addInterceptor { chain ->
            val request = chain.request()
            val body = request.body
            // OkHttp can follow up a 503 with Retry-After: 0 even with connection retries disabled.
            if (request.method == "POST" && body != null) {
                chain.proceed(request.newBuilder().post(object : RequestBody() {
                    override fun contentType() = body.contentType()
                    override fun contentLength() = body.contentLength()
                    override fun isOneShot() = true
                    override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
                }).build())
            } else {
                chain.proceed(request)
            }
        }
    }
}
