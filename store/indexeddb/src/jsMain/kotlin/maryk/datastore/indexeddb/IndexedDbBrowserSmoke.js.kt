@file:OptIn(ExperimentalJsExport::class)

package maryk.datastore.indexeddb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
fun runMarykIndexedDbBrowserSmoke(
    databaseName: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    CoroutineScope(Dispatchers.Default).launch {
        try {
            runIndexedDbBrowserSmoke(databaseName)
            onSuccess()
        } catch (error: Throwable) {
            onError(browserSmokeErrorMessage(error))
        }
    }
}

private fun browserSmokeErrorMessage(error: Throwable): String =
    js("error.stack || error.message || String(error)")
