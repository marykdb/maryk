@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package maryk.datastore.remote

import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import maryk.core.models.key
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.datastore.memory.InMemoryDataStore
import maryk.test.models.SimpleMarykModel
import platform.posix.AF_INET
import platform.posix.INADDR_ANY
import platform.posix.SOCK_STREAM
import platform.posix.bind
import platform.posix.close
import platform.posix.getsockname
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RemoteNativeClientSmokeTest {
    @Test
    fun executesStreamsAndCancelsAgainstNativeServer() = runBlocking {
        withTimeout(30.seconds) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
            val port = allocateLocalPort()
            val server = RemoteStoreServer(store).start(
                host = "127.0.0.1",
                port = port,
                wait = false,
                config = RemoteStoreServerConfig(flowHeartbeatMillis = 100),
            )
            val remote = RemoteDataStore.connect(RemoteStoreConfig(baseUrl = "http://127.0.0.1:$port"))

            try {
                val addResponse: AddResponse<SimpleMarykModel> = remote.execute(
                    SimpleMarykModel.add(SimpleMarykModel.create { value with "ha-native-smoke" })
                )
                val added = assertIs<AddSuccess<SimpleMarykModel>>(addResponse.statuses.single())
                val getResponse: ValuesResponse<SimpleMarykModel> = remote.execute(SimpleMarykModel.get(added.key))
                assertEquals("ha-native-smoke", getResponse.values.single().values { value })

                val firstUpdate = CompletableDeferred<InitialValuesUpdate<SimpleMarykModel>>()
                val collector = launch {
                    remote.executeFlow(SimpleMarykModel.get(added.key)).collect { update ->
                        firstUpdate.complete(assertIs<InitialValuesUpdate<SimpleMarykModel>>(update))
                    }
                }
                withTimeout(5.seconds) { firstUpdate.await() }
                collector.cancelAndJoin()
                assertTrue(collector.isCancelled)

                val responseAfterCancel: ValuesResponse<SimpleMarykModel> =
                    remote.execute(SimpleMarykModel.get(added.key))
                assertEquals("ha-native-smoke", responseAfterCancel.values.single().values { value })
            } finally {
                remote.close()
                server.stop(500, 500)
                store.close()
            }
        }
    }
}

@OptIn(UnsafeNumber::class)
private fun allocateLocalPort(): Int = memScoped {
    val descriptor = socket(AF_INET, SOCK_STREAM, 0)
    check(descriptor >= 0) { "Could not open a socket for the native Remote smoke test" }
    try {
        val address = alloc<sockaddr_in>()
        address.sin_family = AF_INET.convert()
        address.sin_port = 0u
        address.sin_addr.s_addr = INADDR_ANY
        check(bind(descriptor, address.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) == 0) {
            "Could not allocate a local port for the native Remote smoke test"
        }

        val length = alloc<socklen_tVar>()
        length.value = sizeOf<sockaddr_in>().toUInt()
        check(getsockname(descriptor, address.ptr.reinterpret(), length.ptr) == 0) {
            "Could not read the local port for the native Remote smoke test"
        }
        val networkPort = address.sin_port.toInt() and 0xFFFF
        ((networkPort and 0xFF) shl 8) or ((networkPort ushr 8) and 0xFF)
    } finally {
        close(descriptor)
    }
}
