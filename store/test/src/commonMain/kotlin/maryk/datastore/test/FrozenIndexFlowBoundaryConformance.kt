package maryk.datastore.test

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import maryk.core.models.graph
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.test.models.AnyValueMapIndexModel
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

enum class IndexFlowBoundaryOrigin { IncludedStartKey, MovedExcludedStartKey, ProjectedCursor }
enum class IndexCursorFlowChange { StraddlingRowChanges, CursorRowMoves, VisibleRowCrossesCursor }

/** Incremental updates must obey the same canonical-entry cursor rule as finite scans. */
suspend fun assertIndexCursorFlowMembership(store: IsDataStore, descending: Boolean, scenario: IndexCursorFlowChange) =
    withContext(Dispatchers.Default.limitedParallelism(1)) {
        val keys = listOf("a", "b", "c", "d", "straddle").associateWith { name ->
            assertIs<AddSuccess<AnyValueMapIndexModel>>(store.execute(AnyValueMapIndexModel.add(
                AnyValueMapIndexModel.create {
                    this.name with name
                    mapValues with if (name == "straddle") mapOf("0" to "v", "z" to "v") else mapOf(name to "v")
                },
            )).statuses.single()).key
        }
        val boundary = keys.getValue(if (descending) "c" else "b")
        val visible = keys.getValue(if (descending) "b" else "c")
        val barrier = keys.getValue(if (descending) "a" else "d")
        val reference = AnyValueMapIndexModel.ref { mapValues.anyKey() }
        val order = if (descending) reference.descending() else reference.ascending()
        val cursor = assertNotNull(store.execute(AnyValueMapIndexModel.scan(
            startKey = boundary, order = order, limit = 1u,
        )).nextCursor)
        val request = AnyValueMapIndexModel.scan(cursor = cursor, order = order, limit = 10u)
        val events = Channel<IsUpdateResponse<AnyValueMapIndexModel>>(Channel.UNLIMITED)
        val collector = launch { store.executeFlow(request).collect { events.send(it) } }
        try {
            val initial = assertIs<InitialValuesUpdate<AnyValueMapIndexModel>>(withTimeout(10.seconds) { events.receive() })
            assertEquals(listOf(visible, barrier), initial.values.map { it.key })
            // Neither a straddling row nor the cursor row belongs to the next page,
            // even though each has an index entry beyond the frozen boundary.
            val excludedChange = when (scenario) {
                IndexCursorFlowChange.CursorRowMoves -> boundary.change(Change(AnyValueMapIndexModel.ref { mapValues } with
                    mapOf((if (descending) "a" else "y") to "moved")))
                IndexCursorFlowChange.StraddlingRowChanges -> keys.getValue("straddle").change(
                    Change(AnyValueMapIndexModel.ref { name } with "changed straddle"))
                IndexCursorFlowChange.VisibleRowCrossesCursor -> visible.change(
                    Change(AnyValueMapIndexModel.ref { mapValues } with mapOf("0" to "v", "z" to "v")))
            }
            assertIs<ChangeSuccess<AnyValueMapIndexModel>>(store.execute(
                AnyValueMapIndexModel.change(excludedChange),
            ).statuses.single())
            // A visible update is an actor-order barrier for the excluded changes.
            assertIs<ChangeSuccess<AnyValueMapIndexModel>>(store.execute(AnyValueMapIndexModel.change(
                barrier.change(Change(AnyValueMapIndexModel.ref { name } with "visible barrier")),
            )).statuses.single())
            if (scenario == IndexCursorFlowChange.VisibleRowCrossesCursor) {
                val removal = assertIs<RemovalUpdate<AnyValueMapIndexModel>>(withTimeout(10.seconds) { events.receive() })
                assertEquals(visible, removal.key)
            }
            val change = assertIs<ChangeUpdate<AnyValueMapIndexModel>>(withTimeout(10.seconds) { events.receive() })
            assertEquals(barrier, change.key)
            val expected = if (scenario == IndexCursorFlowChange.VisibleRowCrossesCursor) listOf(barrier) else listOf(visible, barrier)
            assertEquals(expected, store.execute(request).values.map { it.key })
        } finally {
            collector.cancelAndJoin()
            events.close()
        }
    }

/** Refill retains a frozen origin even after its start record disappears or moves. */
suspend fun assertFrozenIndexFlowBoundary(
    store: IsDataStore,
    descending: Boolean,
    origin: IndexFlowBoundaryOrigin,
    limit: UInt = 2u,
) = withContext(Dispatchers.Default.limitedParallelism(1)) {
    val keys = (listOf("a", "b", "c", "d", "e", "straddle")).associateWith { name ->
        assertIs<AddSuccess<AnyValueMapIndexModel>>(store.execute(AnyValueMapIndexModel.add(
            AnyValueMapIndexModel.create {
                this.name with name
                mapValues with if (name == "straddle") mapOf("0" to "v", "z" to "v") else mapOf(name to "v")
            },
        )).statuses.single()).key
    }
    val boundaryKey = keys.getValue(if (descending) "d" else "b")
    val reference = AnyValueMapIndexModel.ref { mapValues.anyKey() }
    val order = if (descending) reference.descending() else reference.ascending()
    val select = AnyValueMapIndexModel.graph { listOf(name) }
    val cursor = if (origin == IndexFlowBoundaryOrigin.ProjectedCursor) {
        assertNotNull(store.execute(AnyValueMapIndexModel.scan(
            startKey = boundaryKey, select = select, order = order, limit = 1u,
        )).nextCursor)
    } else null
    val request = AnyValueMapIndexModel.scan(
        startKey = boundaryKey.takeIf { cursor == null }, cursor = cursor,
        select = select, order = order, limit = limit,
        includeStart = origin == IndexFlowBoundaryOrigin.IncludedStartKey,
    )
    val expectedNames = if (descending) listOf("d", "c", "b", "a") else listOf("b", "c", "d", "e")
    val candidates = expectedNames.drop(if (origin == IndexFlowBoundaryOrigin.IncludedStartKey) 0 else 1) +
        if (cursor == null) listOf("straddle") else emptyList()
    val events = Channel<IsUpdateResponse<AnyValueMapIndexModel>>(Channel.UNLIMITED)
    val collector = launch { store.executeFlow(request).collect { events.send(it) } }
    try {
        val initial = assertIs<InitialValuesUpdate<AnyValueMapIndexModel>>(withTimeout(10.seconds) { events.receive() })
        assertEquals(candidates.take(limit.toInt()), initial.values.map { it.values { name } })
        val window = initial.values.map { it.key }.toMutableList()
        if (origin != IndexFlowBoundaryOrigin.IncludedStartKey) {
            assertIs<ChangeSuccess<AnyValueMapIndexModel>>(store.execute(AnyValueMapIndexModel.change(
                boundaryKey.change(Change(AnyValueMapIndexModel.ref { mapValues } with
                    mapOf((if (descending) "zz" else "00") to "moved"))),
            )).statuses.single())
        }
        for (index in limit.toInt() until candidates.size) {
            val removedKey = window.first()
            assertIs<DeleteSuccess<AnyValueMapIndexModel>>(store.execute(
                AnyValueMapIndexModel.delete(removedKey, hardDelete = true),
            ).statuses.single())
            val removal = assertIs<RemovalUpdate<AnyValueMapIndexModel>>(withTimeout(10.seconds) { events.receive() })
            assertEquals(removedKey, removal.key)
            window.removeAt(0)
            val addition = assertIs<AdditionUpdate<AnyValueMapIndexModel>>(withTimeout(10.seconds) { events.receive() })
            assertEquals(candidates[index], addition.values { name })
            assertTrue(addition.key !in window)
            assertEquals(limit.toInt() - 1, addition.insertionIndex)
            assertEquals(AnyValueMapIndexModel.create { name with candidates[index] }, addition.values)
            window.add(addition.key)
            assertEquals(candidates.subList(index - limit.toInt() + 1, index + 1).map(keys::getValue), window)
        }
    } finally {
        collector.cancelAndJoin()
        events.close()
    }
}
