package maryk.datastore.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.models.graph
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.test.models.AnyValueMapIndexModel
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** From-version limits replay history, but an older row can still enter the live window. */
suspend fun assertScanUpdatesOldRecordRefill(store: IsDataStore, descending: Boolean) =
    withContext(Dispatchers.Default.limitedParallelism(1)) {
        coroutineScope {
            val additions = listOf("a", "b", "c", "d").associateWith { name ->
                assertIs<AddSuccess<AnyValueMapIndexModel>>(store.execute(AnyValueMapIndexModel.add(
                    AnyValueMapIndexModel.create {
                        this.name with name
                        mapValues with mapOf(name to "v")
                    },
                )).statuses.single())
            }
            val fromVersion = additions.values.maxOf { it.version } + 1uL
            val names = if (descending) listOf("d", "c", "b", "a") else listOf("a", "b", "c", "d")
            val reference = AnyValueMapIndexModel.ref { mapValues.anyKey() }
            val request = AnyValueMapIndexModel.scanUpdates(
                order = if (descending) reference.descending() else reference.ascending(),
                select = AnyValueMapIndexModel.graph { listOf(name) },
                fromVersion = fromVersion,
                limit = 2u,
            )
            val events = Channel<IsUpdateResponse<AnyValueMapIndexModel>>(Channel.UNLIMITED)
            val collector = launch { store.executeFlow(request).collect { events.send(it) } }
            try {
                val initial = assertIs<OrderedKeysUpdate<AnyValueMapIndexModel>>(withTimeout(10.seconds) { events.receive() })
                assertEquals(names.take(2).map { additions.getValue(it).key }, initial.keys)
                val removed = additions.getValue(names.first())
                val expectedRefill = additions.getValue(names[2])
                val deletion = assertIs<DeleteSuccess<AnyValueMapIndexModel>>(store.execute(
                    AnyValueMapIndexModel.delete(removed.key, hardDelete = true),
                ).statuses.single())
                assertTrue(deletion.version >= fromVersion)
                val removal = assertIs<RemovalUpdate<AnyValueMapIndexModel>>(withTimeout(10.seconds) { events.receive() })
                assertEquals(removed.key, removal.key)
                val refill = assertIs<AdditionUpdate<AnyValueMapIndexModel>>(withTimeout(10.seconds) { events.receive() })
                assertEquals(expectedRefill.key, refill.key)
                assertEquals(expectedRefill.version, refill.version)
                assertEquals(expectedRefill.version, refill.firstVersion)
                assertTrue(refill.version < fromVersion, "New membership must include the older row's current values")
                assertEquals(1, refill.insertionIndex)
                assertEquals(AnyValueMapIndexModel.create { name with names[2] }, refill.values)
            } finally {
                collector.cancelAndJoin()
                events.close()
            }
        }
    }
