package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.models.DataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.SInt64
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.ScanCursor
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.values.Values
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.processors.processScan
import maryk.deleteFolder
import maryk.datastore.test.CareQueryRegressionCases
import maryk.datastore.test.careQueryRegressionModels
import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ReadOptions
import maryk.test.models.Option
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Standalone reproductions of the engine queries reported by Care on Maryk 0.12.1. */
class CareQueryRegressionTest {
    @Test
    fun equalityPrefixWithResidualMembershipKeepsMatchingRows() = runTest {
        withBoundaryCases { it.equalityPrefixWithResidualMembershipKeepsMatchingRows() }
    }

    @Test
    fun compositeMembershipCrossProductKeepsOrderAndContinuation() = runTest {
        withBoundaryCases { it.compositeMembershipCrossProductKeepsOrderAndContinuation() }
    }

    @Test
    fun membershipContinuationTraversesDisjointRanges() = runTest {
        withBoundaryCases { it.membershipContinuationTraversesDisjointRanges() }
    }





    @Test
    fun emptyCompositeMembershipReturnsNoRows() = runTest {
        withBoundaryCases { it.emptyCompositeMembershipReturnsNoRows() }
    }

    @Test
    fun descendingMembershipListenerAcceptsMatchingAddition() = runTest {
        withBoundaryCases { it.descendingMembershipListenerAcceptsMatchingAddition() }
    }

    @Test
    fun compositeMembershipKeepsMatchingRecords() = runTest {
        withBoundaryCases { it.compositeMembershipKeepsMatchingRecords() }
    }



















    private suspend fun withBoundaryCases(block: suspend (CareQueryRegressionCases) -> Unit) {
        val folder = createTestDBFolder("care-byte-boundaries")
        try {
            block(CareQueryRegressionCases {
                RocksDBDataStore.open(
                    relativePath = folder,
                    keepAllVersions = true,
                    keepUpdateHistoryIndex = true,
                    dataModelsById = careQueryRegressionModels,
                )
            })
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun compositeEqualityWithNonLeadingEnum() = runTest {
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

    @Test
    fun compositeEqualityWithTwoEnums() = runTest {
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







    private suspend fun RocksDBDataStore.allPages(
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

    /**
     * Exercise the production scanner with an iterator budget first, then the public request.
     * A coroutine timeout cannot interrupt a runnable native scan worker or its shutdown.
     */
    private suspend fun <DM : IsRootDataModel> RocksDBDataStore.boundedScan(request: ScanRequest<DM>): ValuesResponse<DM> {
        val keys = mutableListOf<Key<DM>>()
        var steps = 0
        val accessor = object : DBAccessor(this) {
            override fun getIterator(readOptions: ReadOptions, columnFamilyHandle: ColumnFamilyHandle): DBIterator =
                object : DBIterator(db.newIterator(columnFamilyHandle, readOptions)) {
                    private fun step() {
                        check(++steps <= 20000) { "Scanner exceeded iterator budget (possible loop)" }
                    }
                    override fun next() { step(); super.next() }
                    override fun prev() { step(); super.prev() }
                    override fun seek(target: ByteArray) { step(); super.seek(target) }
                    override fun seekForPrev(target: ByteArray) { step(); super.seekForPrev(target) }
                }
        }
        ReadOptions().use { options ->
            assertIs<FetchByIndexScan>(processScan(request, accessor, getColumnFamilies(request.dataModel), options) { key, _, _ ->
                keys += key
            })
        }
        return execute(request).also { response ->
            assertIs<FetchByIndexScan>(response.dataFetchType)
            assertEquals(keys, response.values.map { it.key })
        }
    }

    private suspend fun RocksDBDataStore.addRecord(
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

    private suspend fun withStore(name: String, block: suspend (RocksDBDataStore) -> Unit) {
        val folder = createTestDBFolder(name)
        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to QueryRecord, 2u to AuditEvent),
            )
            store.use { block(store) }
        } finally {
            deleteFolder(folder)
        }
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
