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
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.test.models.AnyValueMapIndexModel
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** A row's later index entry must never refill a slot with an already visible key. */
suspend fun assertMultiEntryIndexFlowRefill(store: IsDataStore, descending: Boolean) = withContext(Dispatchers.Default.limitedParallelism(1)) {
    for (name in listOf("a", "b", "c", "d", "e")) {
        assertIs<AddSuccess<AnyValueMapIndexModel>>(store.execute(AnyValueMapIndexModel.add(
            AnyValueMapIndexModel.create {
                this.name with name
                mapValues with if (name == "c") mapOf("c" to "v", "c1" to "v") else mapOf(name to "v")
            },
        )).statuses.single())
    }
    val orderReference = AnyValueMapIndexModel.ref { mapValues.anyKey() }
    val request = AnyValueMapIndexModel.scan(
        select = AnyValueMapIndexModel.graph { listOf(name) },
        order = if (descending) orderReference.descending() else orderReference.ascending(),
        limit = 3u,
    )
    val events = Channel<IsUpdateResponse<AnyValueMapIndexModel>>(Channel.UNLIMITED)
    val listener = launch { store.executeFlow(request).collect { events.send(it) } }
    try {
        val initial = assertIs<InitialValuesUpdate<AnyValueMapIndexModel>>(withTimeout(10.seconds) { events.receive() })
        val window = initial.values.map { it.key }.toMutableList()
        val visibleValues = initial.values.associate { it.key to it.values }.toMutableMap()

        suspend fun checkRefill() {
            val removal = assertIs<RemovalUpdate<AnyValueMapIndexModel>>(withTimeout(10.seconds) { events.receive() })
            assertTrue(window.remove(removal.key))
            visibleValues.remove(removal.key)
            val addition = assertIs<AdditionUpdate<AnyValueMapIndexModel>>(withTimeout(10.seconds) { events.receive() })
            assertTrue(addition.key !in window, "A later index entry duplicated an already visible record")
            window.add(addition.insertionIndex, addition.key)
            visibleValues[addition.key] = addition.values
            val expected = store.execute(request).values
            assertEquals(expected.map { it.key }, window)
            assertEquals(expected.associate { it.key to it.values }, visibleValues)
        }

        assertIs<DeleteSuccess<AnyValueMapIndexModel>>(store.execute(
            AnyValueMapIndexModel.delete(window.first(), hardDelete = true),
        ).statuses.single())
        checkRefill()

        assertIs<ChangeSuccess<AnyValueMapIndexModel>>(store.execute(AnyValueMapIndexModel.change(
            window.first().change(Change(
                AnyValueMapIndexModel.ref { mapValues } with mapOf((if (descending) "0" else "z") to "moved"),
            )),
        )).statuses.single())
        checkRefill()
    } finally {
        listener.cancelAndJoin()
        events.close()
    }
}
