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

internal actual suspend fun <T> withoutWebLocksPlatform(block: suspend () -> T): T {
    val snapshot = captureAndDisableWebLocks()
    return try {
        block()
    } finally {
        restoreWebLocks(snapshot)
    }
}

internal actual suspend fun <T> withThrowingWebLocksPlatform(block: suspend () -> T): T {
    val snapshot = captureAndInstallThrowingWebLocks()
    return try {
        block()
    } finally {
        restoreWebLocks(snapshot)
    }
}

internal actual fun webLocksAvailableForTests(): Boolean = js(
    "typeof navigator !== 'undefined' && !!navigator.locks && typeof navigator.locks.request === 'function'"
)

internal actual fun setLeaseAcquisitionHandoffHookForTests(hook: (() -> Unit)?) {
    indexedDbLeaseAcquisitionHandoffHook = hook
}

private fun captureAndDisableWebLocks(): JsAny = js(
    """
    (function() {
        const hasNavigator = typeof navigator !== "undefined";
        const hadOwnLocks = hasNavigator && Object.prototype.hasOwnProperty.call(navigator, "locks");
        const ownLocksDescriptor = hadOwnLocks
            ? Object.getOwnPropertyDescriptor(navigator, "locks")
            : undefined;
        if (hasNavigator) {
            Object.defineProperty(navigator, "locks", { configurable: true, value: undefined });
        }
        return { hasNavigator, hadOwnLocks, ownLocksDescriptor };
    })()
    """
)

private fun restoreWebLocks(snapshot: JsAny) {
    js(
        """
        if (snapshot.hasNavigator) {
            if (snapshot.hadOwnLocks) {
                Object.defineProperty(navigator, "locks", snapshot.ownLocksDescriptor);
            } else {
                delete navigator.locks;
            }
        }
        """
    )
}

private fun captureAndInstallThrowingWebLocks(): JsAny = js(
    """
    (function() {
        const hasNavigator = typeof navigator !== "undefined";
        const hadOwnLocks = hasNavigator && Object.prototype.hasOwnProperty.call(navigator, "locks");
        const ownLocksDescriptor = hadOwnLocks
            ? Object.getOwnPropertyDescriptor(navigator, "locks")
            : undefined;
        if (hasNavigator) {
            Object.defineProperty(navigator, "locks", {
                configurable: true,
                get() {
                    throw new Error("Web Locks getter failed");
                }
            });
        }
        return { hasNavigator, hadOwnLocks, ownLocksDescriptor };
    })()
    """
)
