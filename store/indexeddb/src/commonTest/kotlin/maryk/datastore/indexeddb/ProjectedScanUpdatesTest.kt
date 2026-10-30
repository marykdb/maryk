package maryk.datastore.indexeddb

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import maryk.core.models.emptyValues
import maryk.core.models.graph
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Order
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.FetchByTableScan
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.processUpdateResponse
import maryk.datastore.test.LaggedSnapshotRecord
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class ProjectedScanUpdatesTest {
    @Test
    fun filtersUseUnselectedValuesInCurrentAndHistoricTableIndexAndHistoryScans() = runTest {
        installIndexedDbForTests()
        val model = LaggedSnapshotRecord
        for (history in listOf(false, true)) {
            val store = IndexedDbDataStore.open(
                "projected-scan-updates-$history-${Random.nextInt()}", mapOf(1u to model),
                keepAllVersions = history, keepUpdateHistoryIndex = history,
            )
            try {
                suspend fun add(enabled: String, rank: String) = assertIs<AddSuccess<LaggedSnapshotRecord>>(
                    store.execute(model.add(model.create {
                        this.enabled with enabled
                        items with listOf(rank)
                        this.rank with rank
                    })).statuses.single(),
                )
                val initiallyIncluded = add("yes", "a")
                val initiallyExcluded = add("no", "b")
                val historicVersion = initiallyExcluded.version
                store.execute(model.change(
                    initiallyIncluded.key.change(Change(model.ref { enabled } with "no")),
                    initiallyExcluded.key.change(Change(model.ref { enabled } with "yes")),
                )).statuses.forEach { assertIs<ChangeSuccess<LaggedSnapshotRecord>>(it) }
                val select = model.graph { listOf(items) }
                for (order in listOf(Order.ascending, model.ref { rank }.ascending(), null)) {
                    for (toVersion in if (history) listOf(null, historicVersion) else listOf(null)) {
                        val response = store.execute(model.scanUpdates(
                            where = Equals(model.ref { enabled } with "yes"), order = order,
                            select = select, toVersion = toVersion, orderedKeys = emptyList(),
                        ))
                        when {
                            order == null && history -> assertIs<FetchByUpdateHistoryIndex>(response.dataFetchType)
                            order?.propertyReference != null -> assertIs<FetchByIndexScan>(response.dataFetchType)
                            else -> assertIs<FetchByTableScan>(response.dataFetchType)
                        }
                        val expectedKey = if (toVersion == null) initiallyExcluded.key else initiallyIncluded.key
                        assertEquals(listOf(expectedKey), assertIs<OrderedKeysUpdate<LaggedSnapshotRecord>>(response.updates.first()).keys,
                            "Matched keys history=$history order=$order toVersion=$toVersion")
                        val additions = response.updates.filterIsInstance<AdditionUpdate<LaggedSnapshotRecord>>()
                        assertEquals(1, additions.size)
                        val expected = model.emptyValues().copy { items with listOf(if (toVersion == null) "b" else "a") }
                        assertEquals(expected, additions.single().values, "Filter fields must not leak into the projection")
                    }
                }
            } finally { store.close() }
        }
    }

    @Test
    fun publicIndexedFlowReceivesDerivedOrderingChanges() = runTest {
        installIndexedDbForTests()
        val model = LaggedSnapshotRecord
        val store = IndexedDbDataStore.open("public-index-flow-${Random.nextInt()}", mapOf(1u to model))
        try {
            val added = store.execute(model.add(*listOf("m", "n", "z").map { rank -> model.create {
                enabled with "yes"
                items with listOf(rank)
                this.rank with rank
            } }.toTypedArray())).statuses.map { assertIs<AddSuccess<LaggedSnapshotRecord>>(it) }
            val request = model.scan(order = model.ref { rank }.ascending(), limit = 2u,
                select = model.graph { listOf(items) })
            val events = Channel<IsUpdateResponse<LaggedSnapshotRecord>>(Channel.UNLIMITED)
            val collector = launch { store.executeFlow(request).collect(events::send) }
            try {
                var image = processUpdateResponse(withContext(Dispatchers.Default.limitedParallelism(1)) {
                    withTimeout(10.seconds) { events.receive() }
                }, emptyList())
                for ((index, rank) in listOf(2 to "a", 2 to "zz", 1 to "b")) {
                    val result = assertIs<ChangeSuccess<LaggedSnapshotRecord>>(store.execute(model.change(
                        added[index].key.change(Change(model.ref { this.rank } with rank)),
                    )).statuses.single())
                    val expected = store.execute(request).values
                    var observedChange = false
                    withContext(Dispatchers.Default.limitedParallelism(1)) {
                        withTimeout(10.seconds) {
                            do {
                                val event = events.receive()
                                observedChange = observedChange || event.version == result.version
                                image = processUpdateResponse(event, image)
                            } while (!observedChange || image.map { it.key } != expected.map { it.key })
                        }
                    }
                    assertEquals(expected, image)
                }
            } finally {
                collector.cancelAndJoin()
                events.close()
            }
        } finally { store.close() }
    }
}
