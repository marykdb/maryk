@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package maryk.datastore.indexeddb

import kotlin.js.JsAny
import kotlin.js.JsName
import kotlin.js.js

@JsModule("fake-indexeddb/lib/fakeIndexedDB")
private external val fakeIndexedDb: JsAny

@JsModule("fake-indexeddb/lib/FDBKeyRange")
private external val fakeIdbKeyRange: JsAny

private fun installFakeIndexedDb(indexedDb: JsAny, idbKeyRange: JsAny) {
    js(
        """
        if (globalThis.indexedDB === undefined) {
            globalThis.indexedDB = indexedDb;
        }
        if (globalThis.IDBKeyRange === undefined) {
            globalThis.IDBKeyRange = idbKeyRange;
        }
        """
    )
}

internal actual fun installIndexedDbForTests() {
    installFakeIndexedDb(fakeIndexedDb, fakeIdbKeyRange)
}
