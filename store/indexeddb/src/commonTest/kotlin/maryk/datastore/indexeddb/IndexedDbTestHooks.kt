package maryk.datastore.indexeddb

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal expect fun installIndexedDbForTests()

private val webLocksTestMutex = Mutex()

internal suspend fun <T> withoutWebLocks(block: suspend () -> T): T = webLocksTestMutex.withLock {
    withoutWebLocksPlatform(block)
}

internal expect suspend fun <T> withoutWebLocksPlatform(block: suspend () -> T): T

internal suspend fun <T> withThrowingWebLocks(block: suspend () -> T): T = webLocksTestMutex.withLock {
    withThrowingWebLocksPlatform(block)
}

internal expect suspend fun <T> withThrowingWebLocksPlatform(block: suspend () -> T): T

internal expect fun webLocksAvailableForTests(): Boolean

internal expect suspend fun <T> withFixedWallClockForTests(
    epochMillis: Double,
    block: suspend () -> T,
): T

internal expect suspend fun <T> withoutBroadcastChannelForTests(block: suspend () -> T): T

internal expect fun setLeaseAcquisitionHandoffHookForTests(hook: (() -> Unit)?)

internal expect fun setOpenResumeHookForTests(hook: (() -> Unit)?)

internal expect fun installCursorContinueHookForTests(hook: () -> Unit): () -> Int
