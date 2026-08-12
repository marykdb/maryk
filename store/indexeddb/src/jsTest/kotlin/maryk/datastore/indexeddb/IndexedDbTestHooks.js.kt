package maryk.datastore.indexeddb

import kotlin.js.js

internal actual fun installIndexedDbForTests() {
    installFakeIndexedDb()
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

internal actual fun installCursorContinueHookForTests(hook: () -> Unit): () -> Int = js(
    """
    (function() {
        const prototype = globalThis.IDBCursor.prototype;
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

private fun captureAndDisableWebLocks(): dynamic = js(
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

private fun replaceDateNow(epochMillis: Double): dynamic = js(
    "(function() { const original = Date.now; Date.now = () => epochMillis; return original; })()"
)

private fun captureAndDisableBroadcastChannel(): dynamic = js(
    "(function() { const value = globalThis.BroadcastChannel; globalThis.BroadcastChannel = undefined; return value; })()"
)

private fun restoreBroadcastChannel(snapshot: dynamic) {
    js("globalThis.BroadcastChannel = snapshot")
}

private fun restoreDateNow(originalNow: dynamic) {
    js("Date.now = originalNow")
}

private fun restoreWebLocks(snapshot: dynamic) {
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

private fun captureAndInstallThrowingWebLocks(): dynamic = js(
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
