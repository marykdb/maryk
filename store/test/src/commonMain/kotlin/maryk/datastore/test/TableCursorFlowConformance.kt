package maryk.datastore.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.models.key
import maryk.core.properties.definitions.string
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.orders.Order
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
import maryk.core.query.responses.updates.processUpdateResponse
import maryk.datastore.shared.IsDataStore
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

object TableCursorFlowRecord : RootDataModel<TableCursorFlowRecord>() {
    val label by string(1u)
    val hidden by string(2u)
}

/** Cursor boundaries apply to live additions and remain intact after the visible window empties. */
suspend fun assertTableCursorFlow(
    store: IsDataStore,
    descending: Boolean,
    projected: Boolean,
    limit: UInt,
) = withContext(Dispatchers.Default.limitedParallelism(1)) {
    fun key(number: Int) = TableCursorFlowRecord.key(ByteArray(TableCursorFlowRecord.Meta.keyByteSize).also {
        it[it.lastIndex] = number.toByte()
    })
    suspend fun add(number: Int) = assertIs<AddSuccess<TableCursorFlowRecord>>(store.execute(TableCursorFlowRecord.add(
        key(number) to TableCursorFlowRecord.create { label with "row$number"; hidden with "hidden$number" },
    )).statuses.single())
    for (number in listOf(10, 20, 30, 40)) add(number)
    val order = if (descending) Order.descending else Order.ascending
    val select = if (projected) TableCursorFlowRecord.graph { listOf(label) } else null
    val cursor = assertNotNull(store.execute(TableCursorFlowRecord.scan(
        order = order, select = select, limit = 1u, allowTableScan = true,
    )).nextCursor)
    val request = TableCursorFlowRecord.scan(
        cursor = cursor, order = order, select = select, limit = limit, allowTableScan = true,
    )
    val events = Channel<IsUpdateResponse<TableCursorFlowRecord>>(Channel.UNLIMITED)
    val collector = launch { store.executeFlow(request).collect { events.send(it) } }
    suspend fun next() = withTimeout(10.seconds) { events.receive() }
    try {
        val initial = assertIs<InitialValuesUpdate<TableCursorFlowRecord>>(next())
        var image = processUpdateResponse(initial, emptyList())
        assertEquals(store.execute(request).values, image)

        // An earlier addition must not enter this page. The subsequent visible
        // change is a drain barrier for the same listener's ordered mailbox.
        add(if (descending) 45 else 5)
        assertIs<ChangeSuccess<TableCursorFlowRecord>>(store.execute(TableCursorFlowRecord.change(
            image.first().key.change(Change(TableCursorFlowRecord.ref { label } with "changed")),
        )).statuses.single())
        image = processUpdateResponse(assertIs<ChangeUpdate<TableCursorFlowRecord>>(next()), image)
        assertEquals(store.execute(request).values, image)

        while (image.isNotEmpty()) {
            val oldSize = image.size
            assertIs<DeleteSuccess<TableCursorFlowRecord>>(store.execute(TableCursorFlowRecord.delete(
                image.first().key, hardDelete = true,
            )).statuses.single())
            val expected = store.execute(request).values
            image = processUpdateResponse(assertIs<RemovalUpdate<TableCursorFlowRecord>>(next()), image)
            if (expected.size == oldSize) {
                image = processUpdateResponse(assertIs<AdditionUpdate<TableCursorFlowRecord>>(next()), image)
            }
            assertEquals(expected, image)
        }
    } finally {
        collector.cancelAndJoin()
        events.close()
    }
}
