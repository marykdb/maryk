@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package maryk.datastore.indexeddb

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlin.js.js
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class WasmIndexedDbByteStoreTest {
    @Test
    fun blockedOpenClosesLateSuccessBeforeNextUpgrade() = runTest {
        installIndexedDbForTests()

        val databaseName = "maryk-indexeddb-wasm-blocked-open-${Random.nextInt()}"
        val initial = openIndexedDbByteStore(databaseName, setOf("initial"))
        initial.close()

        val blockingConnection = openNativeIndexedDb(databaseName)
        assertFailsWith<IllegalStateException> {
            openIndexedDbByteStore(databaseName, setOf("initial", "blocked"))
        }
        closeNativeIndexedDb(blockingConnection)
        delay(1)

        val upgraded = openIndexedDbByteStore(databaseName, setOf("initial", "blocked", "next"))
        try {
            upgraded.put("next", byteArrayOf(1), byteArrayOf(10))
            assertContentEquals(byteArrayOf(10), upgraded.get("next", byteArrayOf(1)))
        } finally {
            upgraded.close()
        }
    }
}

private suspend fun openNativeIndexedDb(databaseName: String): JsAny =
    suspendCancellableCoroutine { continuation ->
        openNativeIndexedDb(
            databaseName = databaseName,
            onSuccess = { continuation.resume(it) },
            onError = { continuation.resumeWithException(IllegalStateException(it)) },
        )
    }

private fun openNativeIndexedDb(
    databaseName: String,
    onSuccess: (JsAny) -> Unit,
    onError: (String) -> Unit,
) {
    js(
        """
        const request = globalThis.indexedDB.open(databaseName);
        request.onsuccess = () => onSuccess(request.result);
        request.onerror = () => onError(request.error?.message ?? "IndexedDB open failed");
        """
    )
}

private fun closeNativeIndexedDb(database: JsAny) {
    js("database.close()")
}
