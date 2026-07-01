@file:Suppress("unused")

package maryk.datastore.indexeddb

@JsModule("fake-indexeddb/auto")
@JsNonModule
private external val fakeIndexedDbAuto: Any

internal fun installFakeIndexedDb() {
    fakeIndexedDbAuto
}
