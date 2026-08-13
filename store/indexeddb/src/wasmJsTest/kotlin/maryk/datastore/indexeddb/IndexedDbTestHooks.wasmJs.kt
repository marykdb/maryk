@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package maryk.datastore.indexeddb

import kotlin.js.JsAny
import kotlin.js.js

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
    if (!indexedDbAvailable()) {
        installFakeIndexedDb(nodeFakeIndexedDb(), nodeFakeIdbKeyRange())
    }
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

internal actual suspend fun <T> withFixedWallClockForTests(
    epochMillis: Double,
    block: suspend () -> T,
): T {
    val originalNow = replaceDateNow(epochMillis)
    return try {
        block()
    } finally {
        restoreDateNow(originalNow)
    }
}

internal actual suspend fun <T> withoutBroadcastChannelForTests(block: suspend () -> T): T {
    val snapshot = captureAndDisableBroadcastChannel()
    return try {
        block()
    } finally {
        restoreBroadcastChannel(snapshot)
    }
}

internal actual fun setLeaseAcquisitionHandoffHookForTests(hook: (() -> Unit)?) {
    indexedDbLeaseAcquisitionHandoffHook = hook
}

internal actual fun setOpenResumeHookForTests(hook: (() -> Unit)?) {
    indexedDbOpenResumeHook = hook
}

internal actual fun installCursorContinueHookForTests(hook: () -> Unit): () -> Int =
    installCursorContinueHook(cursorPrototype(), hook)

private fun installCursorContinueHook(cursorPrototype: JsAny, hook: () -> Unit): () -> Int =
    js(
        """
    (function() {
        const prototype = cursorPrototype;
        const original = prototype.continue;
        let calls = 0;
        prototype.continue = function(...arguments_) {
            const result = original.apply(this, arguments_);
            calls++;
            hook();
            return result;
        };
        return function() {
            prototype.continue = original;
            return calls;
        };
    })()
    """
    )

private fun indexedDbAvailable(): Boolean = js("globalThis.indexedDB !== undefined")

private fun nodeFakeIndexedDb(): JsAny = js("require('fake-indexeddb/lib/fakeIndexedDB')")

private fun nodeFakeIdbKeyRange(): JsAny = js("require('fake-indexeddb/lib/FDBKeyRange')")

private fun cursorPrototype(): JsAny = js(
    "globalThis.IDBCursor ? globalThis.IDBCursor.prototype : require('fake-indexeddb/lib/FDBCursor').prototype"
)

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

private fun replaceDateNow(epochMillis: Double): JsAny = js(
    "(function() { const original = Date.now; Date.now = () => epochMillis; return original; })()"
)

private fun captureAndDisableBroadcastChannel(): JsAny? = js(
    "(function() { const value = globalThis.BroadcastChannel; globalThis.BroadcastChannel = undefined; return value; })()"
)

private fun restoreBroadcastChannel(snapshot: JsAny?) {
    js("globalThis.BroadcastChannel = snapshot")
}

private fun restoreDateNow(originalNow: JsAny) {
    js("Date.now = originalNow")
}

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
