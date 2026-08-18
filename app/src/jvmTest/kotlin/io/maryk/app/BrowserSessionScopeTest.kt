package io.maryk.app

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserSessionScopeTest {
    @Test
    fun `closing first session disposes its store while retaining and then disposing second`() = runBlocking {
        val sessions = mutableStateListOf("A", "B")
        val disposed = mutableListOf<String>()
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        val recomposerScope = CoroutineScope(coroutineContext + ImmediateFrameClock).launch {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            yield()
            composition.setContent {
                BrowserSessionScopes(sessions, { it }) { sessionId ->
                    DisposableEffect(Unit) {
                        onDispose { disposed += sessionId }
                    }
                }
            }
            recomposer.awaitIdle()

            sessions.remove("A")
            Snapshot.sendApplyNotifications()
            yield()
            recomposer.awaitIdle()
            assertEquals(listOf("A"), disposed)

            sessions.remove("B")
            Snapshot.sendApplyNotifications()
            yield()
            recomposer.awaitIdle()
            assertEquals(listOf("A", "B"), disposed)
        } finally {
            composition.dispose()
            recomposer.close()
            recomposerScope.cancel()
        }
    }
}

private object ImmediateFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(System.nanoTime())
}

private class UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}
