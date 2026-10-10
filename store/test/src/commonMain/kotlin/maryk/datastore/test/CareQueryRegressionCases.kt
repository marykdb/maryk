package maryk.datastore.test

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDateTime
import maryk.core.models.DataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.graph
import maryk.core.models.emptyValues
import maryk.core.models.key
import maryk.core.models.RootDataModel
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.UInt8
import maryk.core.properties.types.numeric.UInt16
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.change
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.ValueIn
import maryk.core.query.orders.Order
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.ScanCursor
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.delete
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.FetchByTableScan
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.updates.UpdateListenerForScan
import maryk.test.models.Option

/** The Care query shapes shared by the backend regression suites. */
class CareQueryRegressionCases(private val createStore: suspend (String) -> IsDataStore) {
    suspend fun equalityPrefixWithResidualMembershipKeepsMatchingRows() = withContext(Dispatchers.Default) {
        withStore("care-equality-residual-membership") { store ->
            val matching = store.addRecord(status = Option.V1)
            store.addRecord(status = Option.V2)
            for (descending in listOf(false, true)) {
                val order = if (descending) Orders(
                    QueryRecord.ref { participant }.descending(), QueryRecord.ref { updatedAt }.descending(),
                    QueryRecord.ref { actor }.descending(), QueryRecord.ref { status }.descending(),
                ) else Orders(
                    QueryRecord.ref { participant }.ascending(), QueryRecord.ref { updatedAt }.ascending(),
                    QueryRecord.ref { actor }.ascending(), QueryRecord.ref { status }.ascending(),
                )
                for (version in listOf(null, matching.version)) {
                    val response = store.boundedScan(QueryRecord.scan(
                        where = And(
                            Equals(QueryRecord.ref { participant } with "participant", QueryRecord.ref { updatedAt } with 1L),
                            ValueIn(QueryRecord.ref { status } with setOf(Option.V1)),
                        ),
                        order = order,
                        toVersion = version,
                    ))
                    assertEquals(listOf(matching.key), response.values.map { it.key })
                }
            }
        }
    }

    suspend fun compositeMembershipCrossProductKeepsOrderAndContinuation() = withContext(Dispatchers.Default) {
        withStore("care-membership-cross-product") { store ->
            val additions = listOf(1u to 1u, 1u to 3u, 3u to 1u, 3u to 3u).mapIndexed { index, (scopeValue, ordinalValue) ->
                assertIs<AddSuccess<BoundaryRecord>>(store.execute(BoundaryRecord.add(BoundaryRecord.create {
                    id with (index + 1).toUShort()
                    scope with scopeValue.toUByte()
                    ordinal with ordinalValue.toUByte()
                })).statuses.single())
            }
            val filter = And(
                ValueIn(BoundaryRecord.ref { scope } with setOf(3u.toUByte(), 1u.toUByte())),
                ValueIn(BoundaryRecord.ref { ordinal } with setOf(3u.toUByte(), 1u.toUByte())),
            )
            for (reversed in listOf(false, true)) {
                val order = if (reversed) Orders(BoundaryRecord.ref { scope }.descending(), BoundaryRecord.ref { ordinal }.descending())
                    else Orders(BoundaryRecord.ref { scope }.ascending(), BoundaryRecord.ref { ordinal }.ascending())
                val expected = additions.map { it.key }.let { if (reversed) it.asReversed() else it }
                for (version in listOf(null, additions.last().version)) {
                    for (rootKeyContinuation in listOf(false, true)) {
                        val keys = mutableListOf<Key<BoundaryRecord>>()
                        var cursor: ScanCursor? = null
                        var startKey: Key<BoundaryRecord>? = null
                        for (pageIndex in 0..expected.size) {
                            val page = store.boundedScan(BoundaryRecord.scan(where = filter, order = order, toVersion = version,
                                limit = 1u, cursor = cursor, startKey = startKey, includeStart = false))
                            val record = page.values.singleOrNull() ?: break
                            assertTrue(record.key !in keys, "Cross product continuation must not repeat records")
                            keys += record.key
                            if (rootKeyContinuation) startKey = record.key else cursor = assertNotNull(page.nextCursor)
                        }
                        assertEquals(expected, keys, "Cross product continuation must not skip matching records")
                    }
                    val complete = store.boundedScan(BoundaryRecord.scan(where = filter, order = order, toVersion = version))
                    assertEquals(expected, complete.values.map { it.key }, "Membership cross products must preserve index order")
                }
            }
        }
    }

    suspend fun membershipContinuationTraversesDisjointRanges() = withContext(Dispatchers.Default) {
        withStore("care-membership-continuation") { store ->
            val additions = (1u..5u).map { idValue ->
                assertIs<AddSuccess<BoundaryRecord>>(store.execute(BoundaryRecord.add(BoundaryRecord.create {
                    id with idValue.toUShort()
                    scope with idValue.toUByte()
                    ordinal with 1u.toUByte()
                })).statuses.single())
            }
            for (indexed in listOf(true)) {
                val filter = if (indexed) And(
                    ValueIn(BoundaryRecord.ref { scope } with setOf(5u.toUByte(), 1u.toUByte(), 3u.toUByte())),
                    Equals(BoundaryRecord.ref { ordinal } with 1u.toUByte()),
                )
                    else ValueIn(BoundaryRecord.ref { id } with setOf(5u.toUShort(), 1u.toUShort(), 3u.toUShort()))
                for (reversed in listOf(false, true)) {
                    val order = if (indexed) {
                        if (reversed) BoundaryRecord.ref { scope }.descending() else BoundaryRecord.ref { scope }.ascending()
                    } else if (reversed) Order.descending else Order.ascending
                    val expected = listOf(additions[0].key, additions[2].key, additions[4].key).let { if (reversed) it.asReversed() else it }
                    for (version in listOf(null, additions.last().version)) {
                        for (rootKeyContinuation in listOf(false, true)) {
                            val keys = mutableListOf<Key<BoundaryRecord>>()
                            val cursors = mutableSetOf<ScanCursor>()
                            var cursor: ScanCursor? = null
                            var startKey: Key<BoundaryRecord>? = null
                            for (pageIndex in 0..expected.size) {
                                val page = withTimeout(10.seconds) {
                                    store.execute(BoundaryRecord.scan(where = filter, order = order, toVersion = version,
                                        limit = 1u, cursor = cursor, startKey = startKey, includeStart = false))
                                }
                                val record = page.values.singleOrNull() ?: break
                                assertTrue(record.key !in keys, "Disjoint ranges repeated a record: indexed=$indexed descending=$reversed historical=${version != null} rootKey=$rootKeyContinuation seen=$keys candidate=${record.key}")
                                keys += record.key
                                val next = assertNotNull(page.nextCursor)
                                assertTrue(cursors.add(next), "Disjoint ranges must advance their cursor")
                                if (rootKeyContinuation) startKey = record.key else cursor = next
                            }
                            assertEquals(expected, keys, "Disjoint range continuation must reach every matching record")
                        }
                    }
                }
            }
        }
    }











    private suspend fun verifyByteBoundaryContinuation(scopeValue: UInt, ids: List<UInt>, descending: Boolean) {
        withStore("care-byte-boundary") { store ->
            val additions = ids.map { id ->
                assertIs<AddSuccess<BoundaryRecord>>(store.execute(BoundaryRecord.add(BoundaryRecord.create {
                    this.id with id.toUShort()
                    this.scope with scopeValue.toUByte()
                    ordinal with 1u.toUByte()
                })).statuses.single())
            }
            val expected = additions.map { it.key }.let { if (descending) it.asReversed() else it }
            val order = if (descending) {
                Orders(BoundaryRecord.ref { scope }.descending(), BoundaryRecord.ref { ordinal }.descending())
            } else {
                Orders(BoundaryRecord.ref { scope }.ascending(), BoundaryRecord.ref { ordinal }.ascending())
            }
            for (version in listOf(null, additions.last().version)) {
                for (rootKeyContinuation in listOf(false, true)) {
                    val keys = mutableListOf<Key<BoundaryRecord>>()
                    val cursors = mutableSetOf<ScanCursor>()
                    var cursor: ScanCursor? = null
                    var startKey: Key<BoundaryRecord>? = null
                    for (pageIndex in 0..ids.size) {
                        val page = store.boundedScan(BoundaryRecord.scan(
                            where = Equals(BoundaryRecord.ref { scope } with scopeValue.toUByte()),
                            order = order, limit = 1u, toVersion = version,
                            cursor = cursor, startKey = startKey, includeStart = false,
                        ))
                        val record = page.values.singleOrNull() ?: break
                        assertTrue(record.key !in keys, "Continuation repeated a boundary record")
                        keys += record.key
                        val next = assertNotNull(page.nextCursor)
                        assertTrue(cursors.add(next), "Continuation repeated a boundary cursor")
                        if (rootKeyContinuation) startKey = record.key else cursor = next
                    }
                    assertEquals(expected, keys, "Boundary traversal at version $version, root key $rootKeyContinuation")
                }
            }
        }
    }

    suspend fun descendingMembershipListenerAcceptsMatchingAddition() = withContext(Dispatchers.Default) {
        withStore("care-membership-listener") { store ->
            suspend fun add(idValue: UShort, scopeValue: UByte) = assertIs<AddSuccess<BoundaryRecord>>(
                store.execute(BoundaryRecord.add(BoundaryRecord.create {
                    id with idValue
                    scope with scopeValue
                    ordinal with 1u.toUByte()
                })).statuses.single()
            )
            add(1u.toUShort(), 1u.toUByte())
            add(3u.toUShort(), 3u.toUByte())
            val request = BoundaryRecord.scan(
                where = And(
                    ValueIn(BoundaryRecord.ref { scope } with setOf(1u.toUByte(), 3u.toUByte())),
                    Equals(BoundaryRecord.ref { ordinal } with 1u.toUByte()),
                ),
                order = BoundaryRecord.ref { scope }.descending(),
            )
            val initial = store.boundedScan(request)
            assertEquals(2, initial.values.size)
            val listener = UpdateListenerForScan(
                request,
                BoundaryRecord.createScanRange(request.where, null, true),
                initial,
            )
            try {
                val addition = add(4u.toUShort(), 3u.toUByte())
                val values = store.execute(BoundaryRecord.get(addition.key)).values.single().values
                assertEquals(0, listener.addValues(addition.key, values), "Descending membership listener must accept its highest matching scope")
            } finally {
                listener.close()
            }
        }
    }

    suspend fun compositeMembershipKeepsMatchingRecords() = withContext(Dispatchers.Default) {
        withStore("care-composite-membership") { store ->
            val additions = (1u..3u).map { idValue ->
                assertIs<AddSuccess<BoundaryRecord>>(store.execute(BoundaryRecord.add(BoundaryRecord.create {
                    id with idValue.toUShort()
                    scope with idValue.toUByte()
                    ordinal with 1u.toUByte()
                })).statuses.single())
            }
            val membership = ValueIn(BoundaryRecord.ref { scope } with setOf(3u.toUByte(), 1u.toUByte()))
            for (filter in listOf(membership, And(membership, Equals(BoundaryRecord.ref { ordinal } with 1u.toUByte())))) {
                for (version in listOf(null, additions.last().version)) {
                    for (reversed in listOf(false, true)) {
                        val order = if (reversed) BoundaryRecord.ref { scope }.descending() else BoundaryRecord.ref { scope }.ascending()
                        val expected = listOf(additions[0].key, additions[2].key).let { if (reversed) it.asReversed() else it }
                        val values = store.boundedScan(BoundaryRecord.scan(where = filter, order = order, toVersion = version))
                        assertEquals(expected, values.values.map { it.key })
                        val changes = withTimeout(10.seconds) {
                            store.execute(BoundaryRecord.scanChanges(where = filter, order = order, toVersion = version))
                        }
                        assertEquals(expected, changes.changes.map { it.key })
                        val updates = withTimeout(10.seconds) {
                            store.execute(BoundaryRecord.scanUpdates(where = filter, order = order, toVersion = version))
                        }
                        assertEquals(expected, updates.updates.filterIsInstance<OrderedKeysUpdate<BoundaryRecord>>().single().keys)
                    }
                }
            }
        }
        withStore("care-composite-string-membership") { store ->
            val additions = listOf("a", "aa", "aaa", "b").map { store.addRecord(message = it) }
            val filter = And(
                ValueIn(QueryRecord.ref { messageType } with setOf("aa", "a")),
                Equals(QueryRecord.ref { status } with Option.V1),
            )
            for (version in listOf(null, additions.last().version)) {
                for (reversed in listOf(false, true)) {
                    val order = if (reversed) {
                        Orders(QueryRecord.ref { messageType }.descending(), QueryRecord.ref { status }.descending(), QueryRecord.ref { updatedAt }.descending())
                    } else {
                        Orders(QueryRecord.ref { messageType }.ascending(), QueryRecord.ref { status }.ascending(), QueryRecord.ref { updatedAt }.ascending())
                    }
                    val expected = additions.take(2).map { it.key }.let { if (reversed) it.asReversed() else it }
                    val values = store.boundedScan(QueryRecord.scan(where = filter, order = order, toVersion = version))
                    assertEquals(expected, values.values.map { it.key }, "Membership must preserve exact string lengths")
                }
            }
        }
    }

    suspend fun emptyCompositeMembershipReturnsNoRows() = withContext(Dispatchers.Default) {
        withStore("care-empty-membership") { store ->
            val added = assertIs<AddSuccess<BoundaryRecord>>(store.execute(BoundaryRecord.add(BoundaryRecord.create {
                id with 1u.toUShort()
                scope with 1u.toUByte()
                ordinal with 1u.toUByte()
            })).statuses.single())
            val filter = ValueIn(BoundaryRecord.ref { scope } with emptySet<UByte>())
            for (version in listOf(null, added.version)) {
                for (order in listOf(BoundaryRecord.ref { scope }.ascending(), BoundaryRecord.ref { scope }.descending())) {
                    val values = withTimeout(10.seconds) {
                        store.execute(BoundaryRecord.scan(where = filter, order = order, toVersion = version))
                    }
                    assertTrue(values.values.isEmpty())
                    val changes = withTimeout(10.seconds) {
                        store.execute(BoundaryRecord.scanChanges(where = filter, order = order, toVersion = version))
                    }
                    assertTrue(changes.changes.isEmpty())
                    val updates = withTimeout(10.seconds) {
                        store.execute(BoundaryRecord.scanUpdates(where = filter, order = order, toVersion = version))
                    }
                    assertTrue(updates.updates.filterIsInstance<OrderedKeysUpdate<BoundaryRecord>>().single().keys.isEmpty())
                }
            }
        }
    }











    private suspend fun verifyTableBoundaryContinuation(reversed: Boolean) = withContext(Dispatchers.Default) {
        withStore("care-table-boundary") { store ->
            val additions = listOf(0u, 1u, 65534u, 65535u).map { idValue ->
                assertIs<AddSuccess<BoundaryRecord>>(store.execute(BoundaryRecord.add(BoundaryRecord.create {
                    id with idValue.toUShort()
                    scope with 1u.toUByte()
                    ordinal with 1u.toUByte()
                })).statuses.single())
            }
            val order = if (reversed) Order.descending else Order.ascending
            val boundary = if (reversed) additions.first().key else additions.last().key
            val adjacent = if (reversed) additions[1].key else additions[2].key
            for (version in listOf(null, additions.last().version)) {
                for (start in listOf(adjacent, boundary)) {
                    for (include in listOf(false, true)) {
                        val expected = if (start == boundary) {
                            if (include) listOf(boundary) else emptyList()
                        } else if (include) listOf(start, boundary) else listOf(boundary)
                        val values = withTimeout(10.seconds) {
                            store.execute(BoundaryRecord.scan(startKey = start, includeStart = include, order = order, toVersion = version))
                        }
                        assertIs<FetchByTableScan>(values.dataFetchType)
                        assertEquals(expected, values.values.map { it.key }, "Table values at $start, inclusive $include, version $version")
                        val changes = withTimeout(10.seconds) {
                            store.execute(BoundaryRecord.scanChanges(startKey = start, includeStart = include, order = order, toVersion = version))
                        }
                        assertEquals(expected, changes.changes.map { it.key }, "Table changes at $start, inclusive $include, version $version")
                        val updates = withTimeout(10.seconds) {
                            store.execute(BoundaryRecord.scanUpdates(startKey = start, includeStart = include, order = order, toVersion = version))
                        }
                        assertEquals(expected, updates.updates.filterIsInstance<OrderedKeysUpdate<BoundaryRecord>>().single().keys,
                            "Table updates at $start, inclusive $include, version $version")
                    }
                }
                val first = withTimeout(10.seconds) {
                    store.execute(BoundaryRecord.scan(startKey = boundary, order = order, toVersion = version, limit = 1u))
                }
                val last = withTimeout(10.seconds) {
                    store.execute(BoundaryRecord.scan(cursor = assertNotNull(first.nextCursor), order = order, toVersion = version))
                }
                assertTrue(last.values.isEmpty(), "Opaque table cursor must exhaust at the boundary")
            }
        }
    }



    suspend fun compositeEqualityListenerAcceptsMatchingAddition() {
        withStore("care-composite-listener") { store ->
            val match = store.addRecord(status = Option.V2)
            val values = store.execute(QueryRecord.get(match.key)).values.single().values
            val request = QueryRecord.scan(
                where = Equals(
                    QueryRecord.ref { messageType } with "Patient",
                    QueryRecord.ref { status } with Option.V2,
                ),
                order = Orders(
                    QueryRecord.ref { messageType }.ascending(),
                    QueryRecord.ref { status }.ascending(),
                    QueryRecord.ref { updatedAt }.ascending(),
                ),
            )
            val listener = UpdateListenerForScan(
                request,
                QueryRecord.createScanRange(request.where, null, true),
                ValuesResponse(QueryRecord, values = emptyList()),
            )
            try {
                listener.process(Update.Addition(QueryRecord, match.key, match.version, values), store)
                assertEquals(listOf(match.key), listener.matchingKeys.value)
            } finally {
                listener.close()
            }
        }
    }

    suspend fun compositeEqualityWithNonLeadingEnum() {
        withStore("care-composite-enum") { store ->
            val match = store.addRecord(message = "Patient", status = Option.V2)
            store.addRecord(message = "Patient", status = Option.V1)
            store.addRecord(message = "Task", status = Option.V2)
            val response = store.boundedScan(QueryRecord.scan(
                where = Equals(
                    QueryRecord.ref { messageType } with "Patient",
                    QueryRecord.ref { status } with Option.V2,
                ),
                order = Orders(
                    QueryRecord.ref { messageType }.ascending(),
                    QueryRecord.ref { status }.ascending(),
                    QueryRecord.ref { updatedAt }.ascending(),
                ),
            ))
            assertEquals(listOf(match.key), response.values.map { it.key })
        }
    }

    suspend fun compositeEqualityWithTwoEnums() {
        withStore("care-composite-two-enums") { store ->
            val match = store.addRecord(direction = Option.V2, status = Option.V2)
            store.addRecord(direction = Option.V1, status = Option.V2)
            store.addRecord(direction = Option.V2, status = Option.V1)
            val response = store.boundedScan(QueryRecord.scan(
                where = Equals(
                    QueryRecord.ref { direction } with Option.V2,
                    QueryRecord.ref { status } with Option.V2,
                ),
                order = Orders(
                    QueryRecord.ref { direction }.ascending(),
                    QueryRecord.ref { status }.ascending(),
                    QueryRecord.ref { deliveryDueAt }.ascending(),
                ),
            ))
            assertEquals(listOf(match.key), response.values.map { it.key })
        }
    }







    private suspend fun verifyStatusContinuation(rootKeyContinuation: Boolean) {
        withStore("care-desc-status-cursor") { store ->
            val older = store.addRecord(time = 0L, direction = Option.V1, status = Option.V3)
            val newer = (1L..105L).map { store.addRecord(time = it, status = Option.V3) }
            val request = QueryRecord.scan(
                where = Equals(QueryRecord.ref { status } with Option.V3),
                order = Orders(QueryRecord.ref { status }.descending(), QueryRecord.ref { updatedAt }.descending()),
                limit = 100u,
            )
            val expected = newer.asReversed().map { it.key } + older.key
            assertEquals(expected, store.allPages(request, rootKeyContinuation))
        }
    }







    private suspend fun IsDataStore.allPages(
        request: ScanRequest<QueryRecord>,
        rootKeyContinuation: Boolean = false,
    ): List<Key<QueryRecord>> {
        val keys = mutableListOf<Key<QueryRecord>>()
        val cursors = mutableSetOf<ScanCursor>()
        var cursor: ScanCursor? = null
        var startKey: Key<QueryRecord>? = null
        repeat(10) {
            val page = boundedScan(QueryRecord.scan(
                where = request.where, order = request.order, limit = request.limit,
                cursor = cursor, startKey = startKey, includeStart = false,
            ))
            val pageKeys = page.values.map { it.key }
            assertTrue(pageKeys.none { it in keys }, "Continuation repeated a record")
            keys += pageKeys
            if (page.values.size.toUInt() < request.limit) return keys
            val next = assertNotNull(page.nextCursor)
            assertTrue(cursors.add(next), "Continuation repeated a cursor")
            if (rootKeyContinuation) startKey = pageKeys.last() else cursor = next
        }
        error("Scan did not exhaust its scope within ten pages")
    }

    private suspend fun <DM : IsRootDataModel> IsDataStore.boundedScan(request: ScanRequest<DM>): ValuesResponse<DM> =
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) { execute(request) }
        }.also { assertIs<FetchByIndexScan>(it.dataFetchType) }

    private suspend fun IsDataStore.addRecord(
        time: Long = 1L,
        message: String = "Patient",
        direction: Option = Option.V2,
        status: Option = Option.V1,
        recipient: Values<Profile>? = null,
    ): AddSuccess<QueryRecord> = assertIs<AddSuccess<QueryRecord>>(execute(QueryRecord.add(QueryRecord.create {
        messageType with message
        this.direction with direction
        this.status with status
        updatedAt with time
        deliveryDueAt with time
        participant with "participant"
        actor with "actor"
        timestamp with time
        familyName with "Mous"
        this.recipient with recipient
        practitioner with Profile.create { label with "practitioner" }
    })).statuses.single())

    private suspend fun withStore(name: String, block: suspend (IsDataStore) -> Unit) {
        val store = createStore(name)
        try { block(store) } finally { store.close() }
    }

}

private object Profile : DataModel<Profile>() {
    val label by string(1u, required = false)
}

private object QueryRecord : RootDataModel<QueryRecord>(indexes = {
    listOf(
        Multiple(QueryRecord.messageType.ref(), QueryRecord.status.ref(), QueryRecord.updatedAt.ref()),
        Multiple(QueryRecord.direction.ref(), QueryRecord.status.ref(), QueryRecord.deliveryDueAt.ref()),
        Multiple(QueryRecord.status.ref(), QueryRecord.updatedAt.ref()),
        Multiple(QueryRecord.participant.ref(), QueryRecord.updatedAt.ref()),
        Multiple(QueryRecord.actor.ref(), QueryRecord.timestamp.ref()),
        QueryRecord.familyName.ref(),
        Multiple(QueryRecord.participant.ref(), QueryRecord.updatedAt.ref(), QueryRecord.actor.ref(), QueryRecord.status.ref()),
    )
}) {
    val messageType by string(1u)
    val direction by enum(2u, enum = Option)
    val status by enum(3u, enum = Option)
    val updatedAt by number(4u, type = SInt64)
    val deliveryDueAt by number(5u, type = SInt64)
    val participant by string(6u)
    val actor by string(7u)
    val timestamp by number(8u, type = SInt64)
    val familyName by string(9u)
    val recipient by embed(10u, required = false, dataModel = { Profile })
    val practitioner by embed(11u, required = false, dataModel = { Profile })
}


private object AuditEvent : RootDataModel<AuditEvent>(indexes = {
    listOf(
        AuditEvent.timestamp.ref(),
        AuditEvent.sequence.ref(),
        Multiple(AuditEvent.actor.ref(), AuditEvent.timestamp.ref()),
        Multiple(AuditEvent.subject.ref(), AuditEvent.timestamp.ref()),
    )
}) {
    val timestamp by dateTime(5u)
    val actor by string(6u, required = false)
    val subject by string(9u, required = false)
    val sequence by number(18u, type = SInt64, required = false)
}

private object BoundaryRecord : RootDataModel<BoundaryRecord>(
    keyDefinition = { BoundaryRecord.id.ref() },
    minimumKeyScanByteRange = 0u,
    indexes = { listOf(Multiple(BoundaryRecord.scope.ref(), BoundaryRecord.ordinal.ref())) },
) {
    val id by number(1u, type = UInt16, final = true)
    val scope by number(2u, type = UInt8, final = true)
    val ordinal by number(3u, type = UInt8, final = true)
}

val careQueryRegressionModels: Map<UInt, IsRootDataModel> = mapOf(1u to QueryRecord, 2u to AuditEvent, 3u to BoundaryRecord)
