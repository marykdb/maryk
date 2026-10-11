package maryk.datastore.test

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.statuses.ChangeSuccess
import kotlinx.coroutines.coroutineScope
import maryk.core.models.key
import maryk.core.query.responses.AtomicFailureCode
import maryk.datastore.shared.atomicCapabilities
import kotlin.time.TimeSource
import maryk.core.query.responses.AtomicResponse
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.atomicRequest
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.executeAtomic
import maryk.datastore.shared.supportsAtomicRequests
import maryk.test.models.Person
import maryk.test.models.SimpleMarykModel
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The same contract runs against every local engine and Remote backends. */
class DataStoreAtomicTest(private val dataStore: IsDataStore) : IsDataStoreTest {
    private lateinit var simpleKey: Key<SimpleMarykModel>
    private lateinit var personKey: Key<Person>
    private lateinit var uniqueKey: Key<UniqueModel>
    private var simpleVersion = 0uL
    private val addedKeys = mutableListOf<Key<SimpleMarykModel>>()
    private fun id(name: String) = "atomic-$name-$simpleKey"

    override val allTests = if (dataStore.supportsAtomicRequests) mapOf(
        "atomicAcrossModels" to ::atomicAcrossModels,
        "atomicLateValidationRollsBack" to ::atomicLateValidationRollsBack,
        "atomicUniqueFailureRollsBackAdd" to ::atomicUniqueFailureRollsBackAdd,
        "atomicNewUniqueClaimsConflict" to ::atomicNewUniqueClaimsConflict,
        "atomicNewUniqueClaimCommits" to ::atomicNewUniqueClaimCommits,
        "atomicDeleteRollsBack" to ::atomicDeleteRollsBack,
        "atomicReadOnlyGuard" to ::atomicReadOnlyGuard,
        "atomicSuccessfulGuardDoesNotMutate" to ::atomicSuccessfulGuardDoesNotMutate,
        "atomicStaleVersionRollsBack" to ::atomicStaleVersionRollsBack,
        "atomicCompetingVersions" to ::atomicCompetingVersions,
        "atomicReceiptReplay" to ::atomicReceiptReplay,
        "atomicFailureDoesNotPublishUpdates" to ::atomicFailureDoesNotPublishUpdates,
        "atomicChecksUseInitialState" to ::atomicChecksUseInitialState,
        "atomicPayloadLimit" to ::atomicPayloadLimit,
        "atomicThousandRecordBoundary" to ::atomicThousandRecordBoundary,
    ) else mapOf("atomicUnsupported" to ::atomicUnsupported)

    override suspend fun initData() {
        val simple = assertIs<AddSuccess<SimpleMarykModel>>(dataStore.execute(SimpleMarykModel.add(
            SimpleMarykModel.create { value with "haha-initial" }
        )).statuses.single())
        simpleKey = simple.key
        simpleVersion = simple.version
        personKey = assertIs<AddSuccess<Person>>(dataStore.execute(Person.add(Person.create {
            firstName with "Atomic"
            surname with "Initial"
        })).statuses.single()).key
        uniqueKey = assertIs<AddSuccess<UniqueModel>>(dataStore.execute(UniqueModel.add(UniqueModel.create {
            email with "atomic@example.test"
        })).statuses.single()).key
    }

    override suspend fun resetData() {
        dataStore.execute(SimpleMarykModel.delete(simpleKey, *addedKeys.toTypedArray(), hardDelete = true))
        dataStore.execute(Person.delete(personKey, hardDelete = true))
        dataStore.execute(UniqueModel.delete(uniqueKey, hardDelete = true))
        addedKeys.clear()
    }

    private suspend fun assertUnchanged() {
        val simple = dataStore.execute(SimpleMarykModel.get(simpleKey, filterSoftDeleted = false)).values.single()
        assertEquals("haha-initial", simple.values[SimpleMarykModel.ref { value }])
        assertEquals(simpleVersion, simple.lastVersion)
        assertEquals(false, simple.isDeleted)
        assertEquals("Initial", dataStore.execute(Person.get(personKey)).values.single().values[Person.ref { surname }])
        if (dataStore.keepAllVersions) {
            assertEquals(1, dataStore.execute(SimpleMarykModel.getChanges(simpleKey)).changes.single().changes.size)
        }
    }

    suspend fun atomicAcrossModels() {
        val result = dataStore.executeAtomic(AtomicRequest(id("models"),
            SimpleMarykModel.change(simpleKey.change(Change(SimpleMarykModel.ref { value } with "haha-changed"))),
            Person.change(personKey.change(Change(Person.ref { surname } with "Changed"))),
        ))
        assertEquals(AtomicStatus.Committed, result.status)
        assertEquals(2, result.responses.size)
        val a = dataStore.execute(SimpleMarykModel.get(simpleKey)).values.single()
        val b = dataStore.execute(Person.get(personKey)).values.single()
        assertEquals("haha-changed", a.values[SimpleMarykModel.ref { value }])
        assertEquals("Changed", b.values[Person.ref { surname }])
        assertEquals(a.lastVersion, b.lastVersion)
    }

    suspend fun atomicLateValidationRollsBack() {
        val result = dataStore.executeAtomic(AtomicRequest(id("validation"),
            SimpleMarykModel.change(simpleKey.change(Change(SimpleMarykModel.ref { value } with "haha-changed"))),
            Person.change(personKey.change(Change(Person.ref { surname } with ""))),
        ))
        assertEquals(AtomicStatus.Rejected, result.status)
        assertEquals(1u, result.failedRequestIndex)
        assertEquals(1, result.responses.size)
        assertUnchanged()
    }

    suspend fun atomicUniqueFailureRollsBackAdd() {
        val request = AtomicRequest(id("unique"),
            SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-new" }),
            UniqueModel.add(UniqueModel.create { email with "atomic@example.test" }),
        )
        val key = assertIs<AddRequest<*>>(request.requests.first()).keysForObjects!!.single()
        assertEquals(AtomicStatus.Rejected, dataStore.executeAtomic(request).status)
        @Suppress("UNCHECKED_CAST")
        assertTrue(dataStore.execute(SimpleMarykModel.get(key as Key<SimpleMarykModel>)).values.isEmpty())
        assertEquals(1, dataStore.execute(UniqueModel.scan(allowTableScan = true)).values.size)
        assertUnchanged()
    }

    suspend fun atomicNewUniqueClaimsConflict() {
        val request = AtomicRequest(id("new-unique"),
            UniqueModel.add(UniqueModel.create { email with "new-atomic@example.test" }),
            UniqueModel.add(UniqueModel.create { email with "new-atomic@example.test" }),
        )
        val rejected = dataStore.executeAtomic(request)
        assertEquals(AtomicStatus.Rejected, rejected.status)
        assertEquals(1u, rejected.failedRequestIndex)
        assertEquals(1, dataStore.execute(UniqueModel.scan(allowTableScan = true)).values.size)
        assertUnchanged()
    }

    suspend fun atomicNewUniqueClaimCommits() {
        val request = AtomicRequest(id("new-unique-success"),
            UniqueModel.add(UniqueModel.create { email with "new-atomic@example.test" }))
        @Suppress("UNCHECKED_CAST")
        val key = assertIs<AddRequest<*>>(request.requests.single()).keysForObjects!!.single() as Key<UniqueModel>
        try {
            assertEquals(AtomicStatus.Committed, dataStore.executeAtomic(request).status)
            assertEquals("new-atomic@example.test", dataStore.execute(UniqueModel.get(key)).values.single().values[UniqueModel.ref { email }])
        } finally { dataStore.execute(UniqueModel.delete(key, hardDelete = true)) }
    }

    suspend fun atomicDeleteRollsBack() {
        val request = AtomicRequest(id("delete"), SimpleMarykModel.delete(simpleKey, lastVersion = simpleVersion),
            Person.change(personKey.change(Change(Person.ref { surname } with ""))))
        assertEquals(AtomicStatus.Rejected, dataStore.executeAtomic(request).status)
        assertUnchanged()
    }

    suspend fun atomicReadOnlyGuard() {
        val request = atomicRequest(id("guard")) {
            expect(SimpleMarykModel, simpleKey, Check(SimpleMarykModel.ref { value } with "haha-other"))
            change(Person, personKey.change(Change(Person.ref { surname } with "Changed")))
        }
        val result = dataStore.executeAtomic(request)
        assertEquals(AtomicStatus.Rejected, result.status)
        assertTrue(result.failedGuard)
        assertUnchanged()
    }

    suspend fun atomicSuccessfulGuardDoesNotMutate() {
        val request = atomicRequest(id("successful-guard")) {
            expectVersion(SimpleMarykModel, simpleKey, simpleVersion)
            expect(SimpleMarykModel, simpleKey, Check(SimpleMarykModel.ref { value } with "haha-initial"))
            change(Person, personKey.change(Change(Person.ref { surname } with "Changed")))
        }
        assertEquals(AtomicStatus.Committed, dataStore.executeAtomic(request).status)
        val unchanged = dataStore.execute(SimpleMarykModel.get(simpleKey)).values.single()
        assertEquals(simpleVersion, unchanged.lastVersion)
        assertEquals("haha-initial", unchanged.values[SimpleMarykModel.ref { value }])
        if (dataStore.keepAllVersions) {
            assertEquals(1, dataStore.execute(SimpleMarykModel.getChanges(simpleKey)).changes.single().changes.size)
        }
    }

    suspend fun atomicStaleVersionRollsBack() {
        val request = AtomicRequest(id("stale"),
            Person.change(personKey.change(Change(Person.ref { surname } with "Changed"))),
            SimpleMarykModel.change(simpleKey.change(Change(SimpleMarykModel.ref { value } with "haha-changed"), lastVersion = simpleVersion - 1u)),
        )
        assertEquals(AtomicStatus.Rejected, dataStore.executeAtomic(request).status)
        assertUnchanged()
    }

    suspend fun atomicCompetingVersions() = coroutineScope {
        fun request(name: String) = AtomicRequest(id(name),
            SimpleMarykModel.change(simpleKey.change(Change(SimpleMarykModel.ref { value } with "haha-$name"), lastVersion = simpleVersion)),
            Person.change(personKey.change(Change(Person.ref { surname } with name))),
        )
        val a = async { dataStore.executeAtomic(request("winnerA")) }
        val b = async { dataStore.executeAtomic(request("winnerB")) }
        val results = listOf(a.await(), b.await())
        assertEquals(1, results.count { it.status == AtomicStatus.Committed })
        assertEquals(1, results.count { it.status == AtomicStatus.Rejected })
        val value = dataStore.execute(SimpleMarykModel.get(simpleKey)).values.single().values[SimpleMarykModel.ref { value }]!!
        assertEquals(value.removePrefix("haha-"), dataStore.execute(Person.get(personKey)).values.single().values[Person.ref { surname }])
    }

    suspend fun atomicReceiptReplay() {
        val request = atomicRequest(id("receipt")) {
            expectVersion(SimpleMarykModel, simpleKey, simpleVersion)
            addedKeys += add(SimpleMarykModel, SimpleMarykModel.create { value with "haha-receipt" })
            change(SimpleMarykModel, simpleKey.change(Change(SimpleMarykModel.ref { value } with "haha-changed")))
        }
        val first = dataStore.executeAtomic(request)
        assertEquals(AtomicStatus.Committed, first.status)
        val version = dataStore.execute(SimpleMarykModel.get(simpleKey)).values.single().lastVersion
        assertEquals(first, dataStore.executeAtomic(request))
        assertEquals(version, dataStore.execute(SimpleMarykModel.get(simpleKey)).values.single().lastVersion)
        val different = AtomicRequest(request.changeSetId, Person.delete(personKey))
        assertEquals(AtomicStatus.Rejected, dataStore.executeAtomic(different).status)
        assertEquals(false, dataStore.execute(Person.get(personKey, filterSoftDeleted = false)).values.single().isDeleted)
    }

    suspend fun atomicChecksUseInitialState() {
        val result = dataStore.executeAtomic(AtomicRequest(id("check-order"),
            SimpleMarykModel.change(simpleKey.change(
                Change(SimpleMarykModel.ref { value } with "haha-changed"),
                Check(SimpleMarykModel.ref { value } with "haha-initial"),
            ))))
        assertEquals(AtomicStatus.Committed, result.status)
        assertEquals("haha-changed", dataStore.execute(SimpleMarykModel.get(simpleKey)).values.single().values[SimpleMarykModel.ref { value }])
    }

    suspend fun atomicFailureDoesNotPublishUpdates() = coroutineScope {
        val events = Channel<IsUpdateResponse<SimpleMarykModel>>(Channel.UNLIMITED)
        val listener = launch { dataStore.executeFlow(SimpleMarykModel.get(simpleKey)).collect { events.send(it) } }
        try {
            events.receive()
            atomicLateValidationRollsBack()
            val result = dataStore.execute(SimpleMarykModel.change(simpleKey.change(Change(SimpleMarykModel.ref { value } with "haha-after-failure"))))
            val version = assertIs<ChangeSuccess<*>>(result.statuses.single()).version
            assertEquals(version, events.receive().version)
        } finally { listener.cancelAndJoin(); events.cancel() }
    }

    suspend fun atomicPayloadLimit() {
        val request = AtomicRequest(id("payload-limit"),
            SimpleMarykModel.add(SimpleMarykModel.create { value with ("ha" + "x".repeat(1_048_576)) }))
        val result = dataStore.executeAtomic(request)
        assertEquals(AtomicStatus.Rejected, result.status)
        assertEquals(AtomicFailureCode.LimitExceeded, result.failure?.code)
        assertEquals("requestBytes", result.failure?.limit)
        assertEquals(1_048_576uL, result.failure?.maximum)
        assertTrue(result.failure!!.actual!! > result.failure!!.maximum!!)
        assertUnchanged()
    }

    suspend fun atomicThousandRecordBoundary() {
        assertEquals(1000u, dataStore.atomicCapabilities?.maxMutations)
        val members = List(1000) { SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-boundary-$it" }) }
        val request = AtomicRequest(id("thousand"), members)
        val keys = request.requests.map { @Suppress("UNCHECKED_CAST") (it as AddRequest<SimpleMarykModel>).keysForObjects!!.single() }
        addedKeys.addAll(keys)
        val start = TimeSource.Monotonic.markNow()
        val committed = dataStore.executeAtomic(request)
        val elapsed = start.elapsedNow()
        assertEquals(AtomicStatus.Committed, committed.status, "Atomic 1000-record commit failed after $elapsed: ${committed.message}")
        assertEquals(1000, committed.responses.size)
        assertEquals(committed, dataStore.executeAtomic(request))
        assertEquals(1000, dataStore.execute(SimpleMarykModel.get(*keys.toTypedArray())).values.size)
        val candidate = AtomicRequest(id("thousand-rollback"), List(999) {
            SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-rollback-$it" })
        } + SimpleMarykModel.add(simpleKey to SimpleMarykModel.create { value with "haha-duplicate" }))
        val candidateKeys = candidate.requests.dropLast(1).map { @Suppress("UNCHECKED_CAST") (it as AddRequest<SimpleMarykModel>).keysForObjects!!.single() }
        addedKeys.addAll(candidateKeys)
        val rollbackStart = TimeSource.Monotonic.markNow()
        val rejected = dataStore.executeAtomic(candidate)
        val rollbackElapsed = rollbackStart.elapsedNow()
        assertEquals(AtomicStatus.Rejected, rejected.status, "Atomic 1000-record rollback failed after $rollbackElapsed: ${rejected.message}")
        assertEquals(999u, rejected.failedRequestIndex)
        assertTrue(dataStore.execute(SimpleMarykModel.get(*candidateKeys.toTypedArray())).values.isEmpty())
        println("Atomic 1000 records ${dataStore::class.simpleName}: commit=$elapsed rollback=$rollbackElapsed")
    }

    suspend fun atomicUnsupported() {
        assertEquals(AtomicStatus.Unsupported, dataStore.executeAtomic(AtomicRequest(id("unsupported"), SimpleMarykModel.delete(simpleKey))).status)
        assertUnchanged()
    }
}

/** Standalone entry point for engines whose normal shared suite is selected case by case. */
suspend fun runAtomicDataStoreTests(dataStore: IsDataStore) {
    val test = DataStoreAtomicTest(dataStore)
    for (case in test.allTests.values) {
        test.initData()
        try { case() } finally { test.resetData() }
    }
}

/** Two independent actors must not both commit the same expected version. */
suspend fun testAtomicCompetingStores(first: IsDataStore, second: IsDataStore) = coroutineScope {
    val simple = assertIs<AddSuccess<SimpleMarykModel>>(first.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-race" })).statuses.single())
    val person = assertIs<AddSuccess<Person>>(first.execute(Person.add(Person.create { firstName with "Atomic"; surname with "Initial" })).statuses.single())
    fun request(label: String) = AtomicRequest("race-$label-${simple.key}",
        SimpleMarykModel.change(simple.key.change(Change(SimpleMarykModel.ref { value } with "haha-$label"), lastVersion = simple.version)),
        Person.change(person.key.change(Change(Person.ref { surname } with label))),
    )
    val a = async { first.executeAtomic(request("a")) }
    val b = async { second.executeAtomic(request("b")) }
    val results = listOf(a.await(), b.await())
    assertEquals(1, results.count { it.status == AtomicStatus.Committed })
    assertEquals(1, results.count { it.status == AtomicStatus.Rejected })
    val value = first.execute(SimpleMarykModel.get(simple.key)).values.single().values[SimpleMarykModel.ref { value }]!!
    assertEquals(value.removePrefix("haha-"), first.execute(Person.get(person.key)).values.single().values[Person.ref { surname }])
}

/** Retain both the normalized request and the successful result while reopening a persistent engine. */
suspend fun createAtomicReceiptForReopen(store: IsDataStore): Pair<AtomicRequest, AtomicResponse> {
    val values = SimpleMarykModel.create { value with "haha-durable-receipt" }
    val key = SimpleMarykModel.key(values)
    val request = AtomicRequest("durable-$key", SimpleMarykModel.add(key to values),
        Person.add(Person.create { firstName with "Durable"; surname with "Receipt" }))
    val response = store.executeAtomic(request)
    assertEquals(AtomicStatus.Committed, response.status)
    return request to response
}
