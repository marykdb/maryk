@file:OptIn(ExperimentalJsExport::class)

package maryk.datastore.indexeddb

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

@JsExport
fun runMarykIndexedDbWasmBrowserSmoke(
    databaseName: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    ::runIndexedDbBrowserSmoke.startCoroutine(
        databaseName,
        object : Continuation<Unit> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<Unit>) {
                result.fold(onSuccess) { error -> onError(error.message ?: error.toString()) }
            }
        },
    )
}
