package maryk.datastore.test

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.extensions.bytes.toULong
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.Change
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Order
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.processUpdateResponse
import maryk.core.values.Values
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.updates.FlowUpdate
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.updates.UpdateListener
import maryk.datastore.shared.updates.UpdateListenerForGet
import maryk.datastore.shared.updates.UpdateListenerForScan
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

object LaggedSnapshotRecord : RootDataModel<LaggedSnapshotRecord>(
    indexes = { listOf(LaggedSnapshotRecord.rank.ref()) }, minimumKeyScanByteRange = 0u,
) {
    val enabled by string(1u)
    val items by list(2u, valueDefinition = StringDefinition())
    val rank by string(3u)
}

enum class LaggedSnapshotQuery { Get, Table, Index }
enum class LaggedSnapshotScenario { Enter, Reincarnate, ExitAndReturn, RefillExisting, RefillPendingAdd }

/** A refill of the removed key still has to publish every intervening edit. */
suspend fun assertSameKeyRefillPreservesChanges(
    store: IsDataStore,
    indexed: Boolean,
    historyOrder: Boolean,
    projected: Boolean,
) = withContext(Dispatchers.Default.limitedParallelism(1)) {
    val model = LaggedSnapshotRecord
    val key = model.key(ByteArray(model.Meta.keyByteSize).also { it[it.lastIndex] = 1 })
    assertIs<AddSuccess<LaggedSnapshotRecord>>(store.execute(model.add(key to model.create {
        enabled with "yes"
        items with listOf("first")
        rank with "candidate"
    })).statuses.single())
    val select = model.graph { listOf(items) }.takeIf { projected }
    val listener = if (historyOrder) {
        val request = model.scanUpdates(limit = 1u, select = select)
        UpdateListenerForScan(request, model.createScanRange(null, null), store.execute(model.scanUpdates(limit = 1u)))
    } else {
        val order = model.ref { rank }.ascending().takeIf { indexed }
        val request = model.scan(limit = 1u, select = select, order = order, allowTableScan = true)
        UpdateListenerForScan(request, model.createScanRange(null, null),
            store.execute(model.scan(limit = 1u, order = order, allowTableScan = true)))
    }
    fun append(value: String) = ListChange(model.ref { items }.change(addValuesToEnd = listOf(value)))
    suspend fun change(vararg changes: IsChange): Update.Change<LaggedSnapshotRecord> {
        val result = assertIs<ChangeSuccess<LaggedSnapshotRecord>>(store.execute(model.change(key.change(*changes))).statuses.single())
        return Update.Change(model, key, result.version, changes.toList() + result.changes.orEmpty())
    }
    val events = Channel<IsUpdateResponse<LaggedSnapshotRecord>>(Channel.UNLIMITED)
    val collector = launch { listener.getFlow().collect { events.send(it) } }
    try {
        var image = emptyList<ValuesWithMetaData<LaggedSnapshotRecord>>()
        val queued = listOf(
            change(ObjectSoftDeleteChange(true), append("while-deleting")),
            change(ObjectSoftDeleteChange(false)),
            change(append("after-restore")),
        )
        for (update in queued) listener.process(update, store)
        val barrier = change(append("barrier"))
        listener.process(barrier, store)
        withTimeout(10.seconds) {
            do {
                val event = events.receive()
                image = processUpdateResponse(event, image)
            } while (event.version != barrier.version)
        }
        val expected = store.execute(model.get(key)).values.map { it.copy(values = it.values.filterWithSelect(select)) }
        assertEquals(expected, image, "Same-key refill indexed=$indexed historyOrder=$historyOrder projected=$projected history=${store.keepAllVersions}")
        assertEquals(listOf(key), listener.matchingKeys.value)
    } finally {
        collector.cancelAndJoin()
        listener.close()
        events.close()
    }
}

/** Commit several real writes before delivering their notifications to the listener. */
suspend fun assertLaggedSnapshotFlow(
    store: IsDataStore,
    query: LaggedSnapshotQuery,
    scenario: LaggedSnapshotScenario,
    projected: Boolean,
) = withContext(Dispatchers.Default.limitedParallelism(1)) {
    val model = LaggedSnapshotRecord
    fun key(id: Int) = model.key(ByteArray(model.Meta.keyByteSize).also { it[it.lastIndex] = id.toByte() })
    val anchor = key(1)
    val subject = key(2)
    fun values(enabled: String, item: String, rank: String) = model.create {
        this.enabled with enabled
        items with listOf(item)
        this.rank with rank
    }
    suspend fun add(key: Key<LaggedSnapshotRecord>, values: Values<LaggedSnapshotRecord>): Update.Addition<LaggedSnapshotRecord> {
        val result = assertIs<AddSuccess<LaggedSnapshotRecord>>(store.execute(model.add(key to values)).statuses.single())
        return Update.Addition(model, key, result.version, values)
    }
    suspend fun change(key: Key<LaggedSnapshotRecord>, vararg changes: IsChange): Update.Change<LaggedSnapshotRecord> {
        val result = assertIs<ChangeSuccess<LaggedSnapshotRecord>>(store.execute(model.change(key.change(*changes))).statuses.single())
        return Update.Change(model, key, result.version, changes.toList() + result.changes.orEmpty())
    }
    suspend fun remove(key: Key<LaggedSnapshotRecord>): Update.Deletion<LaggedSnapshotRecord> {
        val result = assertIs<DeleteSuccess<LaggedSnapshotRecord>>(store.execute(model.delete(key, hardDelete = true)).statuses.single())
        return Update.Deletion(model, key, result.version, true)
    }
    fun append(item: String) = ListChange(model.ref { items }.change(addValuesToEnd = listOf(item)))
    fun enabled(value: String) = Change(model.ref { enabled } with value)
    val refill = scenario == LaggedSnapshotScenario.RefillExisting || scenario == LaggedSnapshotScenario.RefillPendingAdd
    require(!refill || query != LaggedSnapshotQuery.Get)
    if (refill) {
        add(anchor, values("yes", "anchor", "a"))
        if (scenario == LaggedSnapshotScenario.RefillExisting) add(subject, values("yes", "a", "b"))
    } else add(subject, values("no", "a", "b"))

    val select = model.graph { listOf(items) }.takeIf { projected }
    val where = Equals(model.ref { enabled } with "yes")
    val listener: UpdateListener<LaggedSnapshotRecord, *>
    val expected: suspend () -> List<ValuesWithMetaData<LaggedSnapshotRecord>>
    if (query == LaggedSnapshotQuery.Get) {
        val request = model.get(subject, where = where, select = select)
        listener = UpdateListenerForGet(request, store.execute(model.get(subject, where = where)))
        expected = { store.execute(model.get(subject, where = where)).values.map { it.copy(values = it.values.filterWithSelect(select)) } }
    } else {
        val order = model.ref { rank }.ascending().takeIf { query == LaggedSnapshotQuery.Index }
        val request = model.scan(where = where, select = select, order = order, limit = 1u, allowTableScan = true)
        listener = UpdateListenerForScan(request, model.createScanRange(where, null),
            store.execute(model.scan(where = where, order = order, limit = 1u, allowTableScan = true)))
        expected = { store.execute(model.scan(where = where, order = order, limit = 1u, allowTableScan = true)).values
            .map { it.copy(values = it.values.filterWithSelect(select)) } }
    }
    val events = Channel<IsUpdateResponse<LaggedSnapshotRecord>>(Channel.UNLIMITED)
    val collector = launch { listener.getFlow().collect { events.send(it) } }
    try {
        var image = processUpdateResponse(withTimeout(10.seconds) { events.receive() }, emptyList())
        val queued = mutableListOf<Update<LaggedSnapshotRecord>>()
        when (scenario) {
            LaggedSnapshotScenario.Enter -> {
                queued += change(subject, enabled("yes"))
                queued += change(subject, append("b"))
            }
            LaggedSnapshotScenario.Reincarnate -> {
                queued += change(subject, enabled("yes"))
                queued += remove(subject)
                queued += add(subject, values("yes", "new", "b"))
                queued += change(subject, append("b"))
            }
            LaggedSnapshotScenario.ExitAndReturn -> {
                queued += change(subject, enabled("yes"))
                queued += change(subject, enabled("no"), append("b"))
            }
            LaggedSnapshotScenario.RefillExisting -> {
                queued += remove(anchor)
                queued += change(subject, append("b"))
            }
            LaggedSnapshotScenario.RefillPendingAdd -> {
                queued += remove(anchor)
                queued += add(subject, values("yes", "a", "b"))
                queued += change(subject, append("b"))
            }
        }
        for (update in queued) listener.process(update, store)
        if (scenario == LaggedSnapshotScenario.ExitAndReturn) listener.process(change(subject, enabled("yes")), store)
        // This later, non-idempotent delta is also a deterministic barrier for all queued output.
        val barrier = change(subject, append("after-snapshot"))
        listener.process(barrier, store)
        withTimeout(10.seconds) {
            do {
                val event = events.receive()
                image = processUpdateResponse(event, image)
            } while (event.version != barrier.version)
        }
        assertEquals(expected(), image, "$query/$scenario/projected=$projected/history=${store.keepAllVersions}")
        assertEquals(listOf(subject), listener.matchingKeys.value)
    } finally {
        collector.cancelAndJoin()
        listener.close()
        events.close()
        store.execute(model.delete(anchor, subject, hardDelete = true))
    }
}

/** Exercise full and projected rows across every supported listener access path. */
suspend fun assertLaggedSnapshotFlowMatrix(store: IsDataStore) {
    for (query in LaggedSnapshotQuery.entries) for (scenario in LaggedSnapshotScenario.entries) {
        if (query == LaggedSnapshotQuery.Get && scenario in listOf(
                LaggedSnapshotScenario.RefillExisting, LaggedSnapshotScenario.RefillPendingAdd)) continue
        for (projected in listOf(false, true)) assertLaggedSnapshotFlow(store, query, scenario, projected)
    }
    for (projected in listOf(false, true)) for (leavesWindow in listOf(false, true)) {
        assertLaggedIndexSnapshotOrder(store, projected, leavesWindow)
    }
}

/** An old index delta cannot determine the position of a newer fetched snapshot. */
suspend fun assertLaggedIndexSnapshotOrder(store: IsDataStore, projected: Boolean, leavesWindow: Boolean) =
    withContext(Dispatchers.Default.limitedParallelism(1)) {
        val model = LaggedSnapshotRecord
        fun key(id: Int) = model.key(ByteArray(model.Meta.keyByteSize).also { it[it.lastIndex] = id.toByte() })
        val anchor = key(1)
        val tail = key(2)
        val candidate = key(3)
        for ((key, rank) in listOf(anchor to "m", tail to "n", candidate to "z")) {
            assertIs<AddSuccess<LaggedSnapshotRecord>>(store.execute(model.add(key to model.create {
                enabled with "yes"
                items with listOf(rank)
                this.rank with rank
            })).statuses.single())
        }
        val select = model.graph { listOf(items) }.takeIf { projected }
        val request = model.scan(order = model.ref { rank }.ascending(), limit = 2u, select = select)
        val listener = UpdateListenerForScan(request, model.createScanRange(null, null),
            store.execute(model.scan(order = model.ref { rank }.ascending(), limit = 2u)))
        val events = Channel<IsUpdateResponse<LaggedSnapshotRecord>>(Channel.UNLIMITED)
        val collector = launch { listener.getFlow().collect { events.send(it) } }
        // Capture committed notifications: ChangeSuccess need not contain generated index deltas.
        val committedChanges = Channel<Update.Change<LaggedSnapshotRecord>>(Channel.UNLIMITED)
        val notificationCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            assertIs<AbstractDataStore>(store).updateSharedFlow.collect { action ->
                val update = (action as? FlowUpdate)?.update ?: return@collect
                if (update is Update.Change<*> && update.dataModel == model) {
                    @Suppress("UNCHECKED_CAST")
                    committedChanges.send(update as Update.Change<LaggedSnapshotRecord>)
                }
            }
        }
        suspend fun change(key: Key<LaggedSnapshotRecord>, change: IsChange): Update.Change<LaggedSnapshotRecord> {
            val result = assertIs<ChangeSuccess<LaggedSnapshotRecord>>(store.execute(model.change(key.change(change))).statuses.single())
            return withTimeout(10.seconds) { committedChanges.receive() }.also {
                assertEquals(key, it.key)
                assertEquals(result.version, it.version)
            }
        }
        try {
            var image = processUpdateResponse(withTimeout(10.seconds) { events.receive() }, emptyList())
            val queued = listOf(
                change(candidate, Change(model.ref { rank } with "a")),
                change(candidate, Change(model.ref { rank } with if (leavesWindow) "zz" else "l")),
                change(tail, Change(model.ref { rank } with "b")),
            )
            for (update in queued) listener.process(update, store)
            assertEquals(store.execute(request).values.map { it.key }, listener.matchingKeys.value,
                "Delayed indexed snapshot leavesWindow=$leavesWindow projected=$projected")
            val barrier = change(tail, ListChange(model.ref { items }.change(addValuesToEnd = listOf("after-snapshot"))))
            listener.process(barrier, store)
            withTimeout(10.seconds) {
                do {
                    val event = events.receive()
                    image = processUpdateResponse(event, image)
                } while (event.version != barrier.version)
            }
            val current = store.execute(model.scan(order = model.ref { rank }.ascending(), limit = 2u)).values
                .map { it.copy(values = it.values.filterWithSelect(select)) }
            assertEquals(current, image,
                "Delayed indexed values/metadata leavesWindow=$leavesWindow projected=$projected")
        } finally {
            notificationCollector.cancelAndJoin()
            committedChanges.close()
            collector.cancelAndJoin()
            listener.close()
            events.close()
            store.execute(model.delete(anchor, tail, candidate, hardDelete = true))
        }
    }

/** Snapshot versions also determine newest-first membership, independently of delivery order. */
suspend fun assertLaggedUpdateHistorySnapshotOrder(store: IsDataStore, projected: Boolean) =
    withContext(Dispatchers.Default.limitedParallelism(1)) {
        val model = LaggedSnapshotRecord
        fun key(id: Int) = model.key(ByteArray(model.Meta.keyByteSize).also { it[it.lastIndex] = id.toByte() })
        val anchor = key(1)
        val newer = key(2)
        val older = key(3)
        val secondAnchor = key(4)
        for ((key, enabled) in listOf(anchor to "yes", secondAnchor to "yes", newer to "no", older to "no")) {
            assertIs<AddSuccess<LaggedSnapshotRecord>>(store.execute(model.add(key to model.create {
                this.enabled with enabled
                items with listOf("initial")
                rank with "rank"
            })).statuses.single())
        }
        // The newest initial revision changes an unselected field. Sorting metadata must still use it.
        assertIs<ChangeSuccess<LaggedSnapshotRecord>>(store.execute(model.change(
            anchor.change(Change(model.ref { rank } with "unselected latest revision")),
        )).statuses.single())
        val where = Equals(model.ref { enabled } with "yes")
        val select = model.graph { listOf(items) }.takeIf { projected }
        val request = model.scanUpdates(where = where, limit = 2u, select = select)
        val initial = store.execute(request)
        assertIs<FetchByUpdateHistoryIndex>(initial.dataFetchType)
        val ordered = assertIs<OrderedKeysUpdate<LaggedSnapshotRecord>>(initial.updates.first())
        val sortingKeys = assertNotNull(ordered.sortingKeys)
        assertEquals(ordered.keys.size, sortingKeys.size)
        val initialRows = store.execute(model.get(*ordered.keys.toTypedArray())).values.associateBy { it.key }
        ordered.keys.forEachIndexed { index, key ->
            assertEquals(8, sortingKeys[index].bytes.size)
            assertEquals(initialRows.getValue(key).lastVersion, sortingKeys[index].bytes.toULong(),
                "Initial history revision must include unselected changes")
        }
        val listener = UpdateListenerForScan(request, model.createScanRange(where, null), initial)
        val events = Channel<IsUpdateResponse<LaggedSnapshotRecord>>(Channel.UNLIMITED)
        val collector = launch { listener.getFlow().collect { events.send(it) } }
        suspend fun change(key: Key<LaggedSnapshotRecord>, change: IsChange): Update.Change<LaggedSnapshotRecord> {
            val result = assertIs<ChangeSuccess<LaggedSnapshotRecord>>(store.execute(model.change(key.change(change))).statuses.single())
            return Update.Change(model, key, result.version, listOf(change) + result.changes.orEmpty())
        }
        try {
            val keys = assertIs<OrderedKeysUpdate<LaggedSnapshotRecord>>(withTimeout(10.seconds) { events.receive() }).keys
            var image = store.execute(model.get(*keys.toTypedArray())).values
                .map { it.copy(values = it.values.filterWithSelect(select)) }
            val queued = listOf(
                change(newer, Change(model.ref { enabled } with "yes")),
                change(older, Change(model.ref { enabled } with "yes")),
                change(newer, ListChange(model.ref { items }.change(addValuesToEnd = listOf("newer")))),
            )
            for (update in queued) listener.process(update, store)
            val expectedKeys = assertIs<OrderedKeysUpdate<LaggedSnapshotRecord>>(store.execute(request).updates.first()).keys
            assertEquals(listOf(newer, older), expectedKeys)
            assertEquals(expectedKeys, listener.matchingKeys.value, "Snapshots must be ordered by represented revision")
            val barrier = change(newer, ListChange(model.ref { items }.change(addValuesToEnd = listOf("after-snapshot"))))
            listener.process(barrier, store)
            withTimeout(10.seconds) {
                do {
                    val event = events.receive()
                    image = processUpdateResponse(event, image)
                } while (event.version != barrier.version)
            }
            val currentByKey = store.execute(model.get(*expectedKeys.toTypedArray())).values.associateBy { it.key }
            val expected = expectedKeys.map { currentByKey.getValue(it).let { row -> row.copy(values = row.values.filterWithSelect(select)) } }
            assertEquals(expected, image, "Newest-first full row snapshots projected=$projected")
        } finally {
            collector.cancelAndJoin()
            listener.close()
            events.close()
            store.execute(model.delete(anchor, secondAnchor, newer, older, hardDelete = true))
        }
    }

/** A chronological history response can contain an old creation before its complete current snapshot. */
suspend fun assertUpdateHistoryRefillUsesCurrentSnapshot(store: IsDataStore, projected: Boolean) =
    withContext(Dispatchers.Default.limitedParallelism(1)) {
        val model = LaggedSnapshotRecord
        fun key(id: Int) = model.key(ByteArray(model.Meta.keyByteSize).also { it[it.lastIndex] = id.toByte() })
        val candidate = key(1)
        val anchor = key(2)
        assertIs<AddSuccess<LaggedSnapshotRecord>>(store.execute(model.add(candidate to model.create {
            enabled with "yes"
            items with listOf("first")
            rank with "candidate"
        })).statuses.single())
        assertIs<ChangeSuccess<LaggedSnapshotRecord>>(store.execute(model.change(candidate.change(
            ListChange(model.ref { items }.change(addValuesToEnd = listOf("second"))),
        ))).statuses.single())
        assertIs<AddSuccess<LaggedSnapshotRecord>>(store.execute(model.add(anchor to model.create {
            enabled with "yes"
            items with listOf("anchor")
            rank with "anchor"
        })).statuses.single())
        val select = model.graph { listOf(items) }.takeIf { projected }
        val request = model.scanUpdates(limit = 1u, select = select)
        val listener = UpdateListenerForScan(request, model.createScanRange(null, null), store.execute(request))
        val events = Channel<IsUpdateResponse<LaggedSnapshotRecord>>(Channel.UNLIMITED)
        val collector = launch { listener.getFlow().collect { events.send(it) } }
        try {
            assertEquals(listOf(anchor), assertIs<OrderedKeysUpdate<LaggedSnapshotRecord>>(
                withTimeout(10.seconds) { events.receive() },
            ).keys)
            val deletion = assertIs<DeleteSuccess<LaggedSnapshotRecord>>(store.execute(model.delete(anchor, hardDelete = true)).statuses.single())
            listener.process(Update.Deletion(model, anchor, deletion.version, true), store)
            assertEquals(anchor, assertIs<RemovalUpdate<LaggedSnapshotRecord>>(withTimeout(10.seconds) { events.receive() }).key)
            val addition = assertIs<AdditionUpdate<LaggedSnapshotRecord>>(withTimeout(10.seconds) { events.receive() })
            val expected = store.execute(model.get(candidate)).values.single().let { it.copy(values = it.values.filterWithSelect(select)) }
            assertEquals(listOf(expected), processUpdateResponse(addition, emptyList()),
                "Refill must use current values and their actual metadata, projected=$projected")
            assertEquals(listOf(candidate), listener.matchingKeys.value)
        } finally {
            collector.cancelAndJoin()
            listener.close()
            events.close()
            store.execute(model.delete(anchor, candidate, hardDelete = true))
        }
    }

/** Read-ahead can refill with a row before the surviving window, including a queued addition. */
suspend fun assertLaggedRefillPosition(store: IsDataStore, indexed: Boolean, projected: Boolean) =
    withContext(Dispatchers.Default.limitedParallelism(1)) {
        val model = LaggedSnapshotRecord
        fun key(id: Int) = model.key(ByteArray(model.Meta.keyByteSize).also { it[it.lastIndex] = id.toByte() })
        val candidate = key(1)
        val anchor = key(2)
        val tail = key(3)
        fun values(rank: String) = model.create {
            enabled with "yes"
            items with listOf(rank)
            this.rank with rank
        }
        store.execute(model.add(anchor to values("m"), tail to values("n"))).statuses.forEach {
            assertIs<AddSuccess<LaggedSnapshotRecord>>(it)
        }
        if (indexed) assertIs<AddSuccess<LaggedSnapshotRecord>>(store.execute(model.add(candidate to values("z"))).statuses.single())
        val select = model.graph { listOf(items) }.takeIf { projected }
        val order = if (indexed) model.ref { rank }.ascending() else Order.ascending
        val listener: UpdateListener<LaggedSnapshotRecord, *>
        val initialEventCount: Int
        if (indexed) {
            val request = model.scan(order = order, limit = 2u, select = select)
            listener = UpdateListenerForScan(request, model.createScanRange(null, null),
                store.execute(model.scan(order = order, limit = 2u)))
            initialEventCount = 1
        } else {
            val request = model.scanUpdates(order = order, limit = 2u, select = select)
            val response = store.execute(request)
            listener = UpdateListenerForScan(request, model.createScanRange(null, null), response)
            initialEventCount = response.updates.size
        }
        val events = Channel<IsUpdateResponse<LaggedSnapshotRecord>>(Channel.UNLIMITED)
        val collector = launch { listener.getFlow().collect { events.send(it) } }
        try {
            var image = emptyList<ValuesWithMetaData<LaggedSnapshotRecord>>()
            repeat(initialEventCount) {
                image = processUpdateResponse(withTimeout(10.seconds) { events.receive() }, image)
            }
            val deletion = assertIs<DeleteSuccess<LaggedSnapshotRecord>>(store.execute(model.delete(anchor, hardDelete = true)).statuses.single())
            val pending: Update<LaggedSnapshotRecord> = if (indexed) {
                val change = Change(model.ref { rank } with "a")
                val result = assertIs<ChangeSuccess<LaggedSnapshotRecord>>(store.execute(model.change(candidate.change(change))).statuses.single())
                Update.Change(model, candidate, result.version, listOf(change) + result.changes.orEmpty())
            } else {
                val value = values("a")
                val result = assertIs<AddSuccess<LaggedSnapshotRecord>>(store.execute(model.add(candidate to value)).statuses.single())
                Update.Addition(model, candidate, result.version, value)
            }
            listener.process(Update.Deletion(model, anchor, deletion.version, true), store)
            listener.process(pending, store)
            assertEquals(listOf(candidate, tail), listener.matchingKeys.value,
                "Refill position indexed=$indexed projected=$projected")
            val append = ListChange(model.ref { items }.change(addValuesToEnd = listOf("after-refill")))
            val barrier = assertIs<ChangeSuccess<LaggedSnapshotRecord>>(store.execute(model.change(tail.change(append))).statuses.single())
            listener.process(Update.Change(model, tail, barrier.version, listOf(append) + barrier.changes.orEmpty()), store)
            withTimeout(10.seconds) {
                do {
                    val event = events.receive()
                    image = processUpdateResponse(event, image)
                } while (event.version != barrier.version)
            }
            val expected = store.execute(model.scan(order = order, limit = 2u, allowTableScan = true)).values
                .map { it.copy(values = it.values.filterWithSelect(select)) }
            assertEquals(expected, image, "Refill image indexed=$indexed projected=$projected history=${store.keepAllVersions}")
        } finally {
            collector.cancelAndJoin()
            listener.close()
            events.close()
            store.execute(model.delete(candidate, anchor, tail, hardDelete = true))
        }
    }

/** Batch members share one revision, so their input order cannot define history-window ties. */
suspend fun assertUpdateHistoryRevisionTies(
    store: IsDataStore,
    descendingInput: Boolean,
    projected: Boolean,
    limit: UInt,
) = withContext(Dispatchers.Default.limitedParallelism(1)) {
    val model = LaggedSnapshotRecord
    fun key(id: Int) = model.key(ByteArray(model.Meta.keyByteSize).also { it[it.lastIndex] = id.toByte() })
    val ids = if (descendingInput) listOf(3, 2, 1) else listOf(1, 2, 3)
    val select = model.graph { listOf(items) }.takeIf { projected }
    val request = model.scanUpdates(limit = limit, select = select)
    val listener = UpdateListenerForScan(request, model.createScanRange(null, null), store.execute(request))
    val events = Channel<IsUpdateResponse<LaggedSnapshotRecord>>(Channel.UNLIMITED)
    val collector = launch { listener.getFlow().collect { events.send(it) } }
    try {
        assertEquals(emptyList(), assertIs<OrderedKeysUpdate<LaggedSnapshotRecord>>(
            withTimeout(10.seconds) { events.receive() },
        ).keys)
        val inputs = ids.map { id -> key(id) to model.create {
            enabled with "yes"
            items with listOf("row$id")
            rank with "row$id"
        } }
        val added = store.execute(model.add(*inputs.toTypedArray())).statuses.map { assertIs<AddSuccess<LaggedSnapshotRecord>>(it) }
        assertEquals(1, added.map { it.version }.distinct().size)
        for ((index, result) in added.withIndex()) {
            listener.process(Update.Addition(model, result.key, result.version, inputs[index].second), store)
        }
        val expectedKeys = listOf(key(3), key(2), key(1)).take(limit.toInt())
        assertEquals(expectedKeys, assertIs<OrderedKeysUpdate<LaggedSnapshotRecord>>(store.execute(request).updates.first()).keys,
            "Batch additions use descending key ties, descendingInput=$descendingInput")
        assertEquals(expectedKeys, listener.matchingKeys.value)

        val append = ListChange(model.ref { items }.change(addValuesToEnd = listOf("tied-change")))
        val changed = store.execute(model.change(*ids.map { key(it).change(append) }.toTypedArray())).statuses
            .map { assertIs<ChangeSuccess<LaggedSnapshotRecord>>(it) }
        assertEquals(1, changed.map { it.version }.distinct().size)
        for ((index, result) in changed.withIndex()) {
            listener.process(Update.Change(model, key(ids[index]), result.version, listOf(append) + result.changes.orEmpty()), store)
        }
        assertEquals(expectedKeys, assertIs<OrderedKeysUpdate<LaggedSnapshotRecord>>(store.execute(request).updates.first()).keys,
            "Batch changes use descending key ties, descendingInput=$descendingInput")
        assertEquals(expectedKeys, listener.matchingKeys.value)
        val barrierChange = ListChange(model.ref { items }.change(addValuesToEnd = listOf("after-ties")))
        val barrier = assertIs<ChangeSuccess<LaggedSnapshotRecord>>(store.execute(model.change(key(3).change(barrierChange))).statuses.single())
        listener.process(Update.Change(model, key(3), barrier.version, listOf(barrierChange) + barrier.changes.orEmpty()), store)
        var image = emptyList<ValuesWithMetaData<LaggedSnapshotRecord>>()
        withTimeout(10.seconds) {
            do {
                val event = events.receive()
                image = processUpdateResponse(event, image)
            } while (event.version != barrier.version)
        }
        val currentByKey = store.execute(model.get(*expectedKeys.toTypedArray())).values.associateBy { it.key }
        val expected = expectedKeys.map { currentByKey.getValue(it).let { row -> row.copy(values = row.values.filterWithSelect(select)) } }
        assertEquals(expected, image, "Batch history image descendingInput=$descendingInput projected=$projected limit=$limit")
    } finally {
        collector.cancelAndJoin()
        listener.close()
        events.close()
        store.execute(model.delete(key(1), key(2), key(3), hardDelete = true))
    }
}
