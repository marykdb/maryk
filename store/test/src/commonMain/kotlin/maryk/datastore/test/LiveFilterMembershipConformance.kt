package maryk.datastore.test

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.models.RootDataModel
import maryk.core.models.DataModel
import maryk.core.models.key
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.geoPoint
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GeoWithinBox
import maryk.core.query.filters.GeoWithinRadius
import maryk.core.query.filters.GeoWithinPolygon
import maryk.core.properties.types.GeoPoint
import maryk.core.query.filters.Matches
import maryk.core.query.filters.MatchesPrefix
import maryk.core.query.filters.MatchesRegEx
import maryk.core.query.filters.Not
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

object LiveReferencedRecord : RootDataModel<LiveReferencedRecord>(
    minimumKeyScanByteRange = 0u,
    indexes = { listOf(AnyOf("names", LiveReferencedRecord.name.ref())) },
) {
    val name by string(1u)
    val linked by reference(2u, required = false, dataModel = { LiveReferencedRecord })
    val details by embed(3u, required = false, dataModel = { LiveFilterPlace })
}

object LiveFilterPlace : DataModel<LiveFilterPlace>() {
    val location by geoPoint(1u, required = true, final = true)
}

enum class LiveGeoFilter { Box, Radius, Polygon }

/** A final location may disappear or return with its optional enclosing object. */
suspend fun assertGeoParentFilterChanges(store: IsDataStore, kind: LiveGeoFilter, getQuery: Boolean) =
    withContext(Dispatchers.Default.limitedParallelism(1)) {
        val model = LiveReferencedRecord
        val details = LiveFilterPlace.create { location with GeoPoint(0.0, 0.0) }
        val key = assertIs<AddSuccess<LiveReferencedRecord>>(store.execute(model.add(model.create {
            name with "place"
            this.details with details
        })).statuses.single()).key
        val reference = model.ref { this.details { location } }
        val filter = when (kind) {
            LiveGeoFilter.Box -> GeoWithinBox(reference, -1.0, -1.0, 1.0, 1.0)
            LiveGeoFilter.Radius -> GeoWithinRadius(reference, GeoPoint(0.0, 0.0), 1000.0)
            LiveGeoFilter.Polygon -> GeoWithinPolygon(reference,
                GeoPoint(-1.0, -1.0), GeoPoint(-1.0, 1.0), GeoPoint(1.0, 0.0))
        }
        val events = Channel<IsUpdateResponse<LiveReferencedRecord>>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            val flow = if (getQuery) store.executeFlow(model.get(key, where = filter))
            else store.executeFlow(model.scan(where = filter, allowTableScan = true))
            flow.collect { events.send(it) }
        }
        try {
            val initial = assertIs<InitialValuesUpdate<LiveReferencedRecord>>(withTimeout(10.seconds) { events.receive() })
            assertEquals(listOf(key), initial.values.map { it.key })
            assertIs<ChangeSuccess<LiveReferencedRecord>>(store.execute(model.change(
                key.change(Change(model.ref { this.details } with null)),
            )).statuses.single())
            val removal = assertIs<RemovalUpdate<LiveReferencedRecord>>(withTimeout(10.seconds) { events.receive() })
            assertEquals(key, removal.key)
            assertEquals(emptyList(), store.execute(model.get(key, where = filter)).values)
            assertIs<ChangeSuccess<LiveReferencedRecord>>(store.execute(model.change(
                key.change(Change(model.ref { this.details } with details)),
            )).statuses.single())
            val addition = assertIs<AdditionUpdate<LiveReferencedRecord>>(withTimeout(10.seconds) { events.receive() })
            assertEquals(key, addition.key)
        } finally {
            collector.cancelAndJoin()
            events.close()
        }
    }

enum class LiveSearchFilter { Exact, Prefix, RegEx }

/** Named predicates inside logical filters must re-evaluate mutable membership. */
suspend fun assertNamedFilterChanges(
    store: IsDataStore,
    filterKind: LiveSearchFilter,
    negated: Boolean,
    getQuery: Boolean,
) = withContext(Dispatchers.Default.limitedParallelism(1)) {
    val model = LiveReferencedRecord
    val matchingName = if (negated) "other" else "match"
    val excludedName = if (negated) "match" else "other"
    val key = assertIs<AddSuccess<LiveReferencedRecord>>(store.execute(model.add(model.create {
        name with matchingName
    })).statuses.single()).key
    val named = when (filterKind) {
        LiveSearchFilter.Exact -> Matches("names" with "match")
        LiveSearchFilter.Prefix -> MatchesPrefix("names" with "ma")
        LiveSearchFilter.RegEx -> MatchesRegEx("names" with Regex("^ma.*$"))
    }
    val filter = if (negated) Not(named) else named
    val events = Channel<IsUpdateResponse<LiveReferencedRecord>>(Channel.UNLIMITED)
    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        val flow = if (getQuery) store.executeFlow(model.get(key, where = filter))
        else store.executeFlow(model.scan(where = filter, allowTableScan = true))
        flow.collect { events.send(it) }
    }
    try {
        val initial = assertIs<InitialValuesUpdate<LiveReferencedRecord>>(withTimeout(10.seconds) { events.receive() })
        assertEquals(listOf(key), initial.values.map { it.key })
        assertIs<ChangeSuccess<LiveReferencedRecord>>(store.execute(model.change(
            key.change(Change(model.ref { name } with excludedName)),
        )).statuses.single())
        assertEquals(emptyList(), store.execute(model.get(key, where = filter)).values)
        val removal = assertIs<RemovalUpdate<LiveReferencedRecord>>(withTimeout(10.seconds) { events.receive() })
        assertEquals(key, removal.key)
        assertIs<ChangeSuccess<LiveReferencedRecord>>(store.execute(model.change(
            key.change(Change(model.ref { name } with matchingName)),
        )).statuses.single())
        val addition = assertIs<AdditionUpdate<LiveReferencedRecord>>(withTimeout(10.seconds) { events.receive() })
        assertEquals(key, addition.key)
        assertEquals(matchingName, addition.values { name })
    } finally {
        collector.cancelAndJoin()
        events.close()
    }
}

/** An addition must use the same referenced filter semantics as its initial read. */
suspend fun assertReferencedFilterAddition(store: IsDataStore, getQuery: Boolean) = withContext(Dispatchers.Default.limitedParallelism(1)) {
    val model = LiveReferencedRecord
    val target = assertIs<AddSuccess<LiveReferencedRecord>>(store.execute(model.add(model.create {
        name with "matching target"
    })).statuses.single()).key
    val otherTarget = assertIs<AddSuccess<LiveReferencedRecord>>(store.execute(model.add(model.create {
        name with "different target"
    })).statuses.single()).key
    val source = model.key(ByteArray(model.Meta.keyByteSize) { 1 })
    val excluded = model.key(ByteArray(model.Meta.keyByteSize) { 2 })
    val filter = Equals(model.ref { linked { name } } with "matching target")
    val updates = Channel<IsUpdateResponse<LiveReferencedRecord>>(Channel.UNLIMITED)
    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        val flow = if (getQuery) store.executeFlow(model.get(source, excluded, where = filter))
        else store.executeFlow(model.scan(where = filter, allowTableScan = true))
        flow.collect { updates.send(it) }
    }
    try {
        val initial = assertIs<InitialValuesUpdate<LiveReferencedRecord>>(withTimeout(10.seconds) { updates.receive() })
        assertEquals(emptyList(), initial.values)
        assertIs<AddSuccess<LiveReferencedRecord>>(store.execute(model.add(excluded to model.create {
            name with "excluded source"
            linked with otherTarget
        })).statuses.single())
        val values = model.create {
            name with "source"
            linked with target
        }
        assertIs<AddSuccess<LiveReferencedRecord>>(store.execute(model.add(source to values)).statuses.single())
        assertEquals(listOf(source), store.execute(model.get(source, where = filter)).values.map { it.key })
        val addition = assertIs<AdditionUpdate<LiveReferencedRecord>>(withTimeout(10.seconds) { updates.receive() })
        assertEquals(source, addition.key)
        assertEquals(values, addition.values)
    } finally {
        collector.cancelAndJoin()
        updates.close()
    }
}
