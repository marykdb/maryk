@file:Suppress("unused")

package maryk.datastore.indexeddb

import kotlin.js.js

internal fun installFakeIndexedDb() {
    js("if (globalThis.indexedDB === undefined) eval('require')('fake-indexeddb/auto')")
}
