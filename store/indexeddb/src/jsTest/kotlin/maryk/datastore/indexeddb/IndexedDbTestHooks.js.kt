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

internal actual fun setLeaseAcquisitionHandoffHookForTests(hook: (() -> Unit)?) {
    indexedDbLeaseAcquisitionHandoffHook = hook
}

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
