package maryk.datastore.test

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import maryk.core.models.fromChanges
import maryk.core.models.graph
import maryk.core.properties.graph.get
import maryk.core.properties.graph.graph
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.processUpdateResponse
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.updates.UpdateListenerForGet
import maryk.test.models.SelectionIntegrityCase
import maryk.test.models.SelectionIntegrityLeaf
import maryk.test.models.SelectionIntegrityModel
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

suspend fun assertSelectionIntegrity(store: IsDataStore) = coroutineScope {
    val model = SelectionIntegrityModel
    val leaf = SelectionIntegrityLeaf.create {
        first with "first-visible"
        second with "second-visible"
    }
    val pointKey = GeoPoint(1.234567891, -2.500000019)
    val storedPointKey = GeoPoint(pointKey.latitudeAsInt(), pointKey.longitudeAsInt())
    val original = model.create {
        records with mapOf("a" to leaf, "b" to leaf, "unselected" to leaf)
        points with mapOf(storedPointKey to "selected-point", GeoPoint(0, 0) to "other-point")
        details with leaf
        allocated with mapOf(1u to leaf, 2u to leaf)
        typed with SelectionIntegrityCase.B(leaf)
    }
    val added = assertIs<AddSuccess<SelectionIntegrityModel>>(store.execute(model.add(original)).statuses.single())
    val select = model.graph {
        listOf(records.graph("a") { listOf(first) }, records.graph("b") { listOf(second) },
            graph(details) { listOf(first) }, graph(details) { listOf(second) },
            allocated.graph(1u) { listOf(first) }, points[pointKey],
            typed.withTypeGraph(SelectionIntegrityCase.A) { listOf(first) },
            typed.withTypeGraph(SelectionIntegrityCase.B) { listOf(second) })
    }
    val firstOnly = SelectionIntegrityLeaf.create { first with "first-visible" }
    val secondOnly = SelectionIntegrityLeaf.create { second with "second-visible" }
    val expected = model.create {
        records with mapOf("a" to firstOnly, "b" to secondOnly)
        details with leaf
        allocated with mapOf(1u to firstOnly)
        points with mapOf(storedPointKey to "selected-point")
        typed with SelectionIntegrityCase.B(secondOnly)
    }
    for (version in listOf(null, added.version)) {
        val result = store.execute(model.get(added.key, select = select, toVersion = version)).values.single().values
        assertEquals(expected, result, "get projection, version=$version")
    }
    val full = store.execute(model.get(added.key))
    val listener = UpdateListenerForGet(model.get(added.key, select = select), full)
    val events = Channel<IsUpdateResponse<SelectionIntegrityModel>>(Channel.UNLIMITED)
    val collector = launch { listener.getFlow().collect { events.send(it) } }
    try {
        val initial = assertIs<InitialValuesUpdate<SelectionIntegrityModel>>(withTimeout(10.seconds) { events.receive() })
        assertEquals(expected, initial.values.single().values, "live initial snapshot must match the literal projection")
        assertEquals(original, store.execute(model.get(added.key)).values.single().values)
        val newLeaf = SelectionIntegrityLeaf.create {
            first with "new-first"
            second with "new-second"
        }
        val replacements = listOf(Check(model.ref { typed } with SelectionIntegrityCase.B(leaf)), Change(
            model.ref { records } with mapOf("a" to newLeaf, "b" to newLeaf, "extra" to newLeaf),
            model.ref { details } with newLeaf,
            model.ref { typed } with SelectionIntegrityCase.B(newLeaf),
        ), IncMapChange(model.ref { allocated }.change(addValues = listOf(newLeaf))))
        val changed = assertIs<ChangeSuccess<SelectionIntegrityModel>>(
            store.execute(model.change(added.key.change(*replacements.toTypedArray()))).statuses.single())
        listener.process(Update.Change(model, added.key, changed.version, replacements + changed.changes.orEmpty()), store)
        val event = assertIs<ChangeUpdate<SelectionIntegrityModel>>(withTimeout(10.seconds) { events.receive() })
        assertEquals(listOf(Check(model.ref { typed } with SelectionIntegrityCase.B(secondOnly))),
            event.changes.filterIsInstance<Check>(), "check operands in notifications follow the selection")
        val newExpected = model.create {
            records with mapOf("a" to SelectionIntegrityLeaf.create { first with "new-first" },
                "b" to SelectionIntegrityLeaf.create { second with "new-second" })
            details with newLeaf
            allocated with mapOf(1u to firstOnly)
            points with mapOf(storedPointKey to "selected-point")
            typed with SelectionIntegrityCase.B(SelectionIntegrityLeaf.create { second with "new-second" })
        }
        assertEquals(newExpected, processUpdateResponse(event, initial.values).single().values, "live replacement")
        for (version in listOf(null, changed.version)) {
            assertEquals(newExpected, store.execute(model.get(added.key, select = select, toVersion = version)).values.single().values)
        }
        val history = store.execute(model.getChanges(added.key, select = select)).changes.single().changes
        assertEquals(newExpected, model.fromChanges(null, history.flatMap { it.changes }), "selected history replay")
    } finally {
        listener.close()
        collector.cancelAndJoin()
        events.close()
    }
}
