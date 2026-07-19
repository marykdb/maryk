package maryk.datastore.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.requests.ScanCursor
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import maryk.test.models.AnyValueIncMapIndexModel
import maryk.test.models.AnyValueMapIndexModel
import maryk.test.models.AnyValueSetIndexModel
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.expect

class DataStoreScanOnAnyValueIndexTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val mapKeys = mutableListOf<Key<AnyValueMapIndexModel>>()
    private val incMapKeys = mutableListOf<Key<AnyValueIncMapIndexModel>>()
    private val setKeys = mutableListOf<Key<AnyValueSetIndexModel>>()

    override val allTests = mapOf(
        "executeScanOnMapRefToAnyIndexRequest" to ::executeScanOnMapRefToAnyIndexRequest,
        "executeScanOnIncMapRefToAnyIndexRequest" to ::executeScanOnIncMapRefToAnyIndexRequest,
        "executeScanOnSetRefToAnyIndexRequest" to ::executeScanOnSetRefToAnyIndexRequest,
        "executeFilterOnMapRefToAnyIndexRequest" to ::executeFilterOnMapRefToAnyIndexRequest,
        "executeFilterOnIncMapRefToAnyIndexRequest" to ::executeFilterOnIncMapRefToAnyIndexRequest,
        "executeFilterOnSetRefToAnyIndexRequest" to ::executeFilterOnSetRefToAnyIndexRequest,
        "executeInitialAddIndexesAllMapKeys" to ::executeInitialAddIndexesAllMapKeys,
        "executeInitialAddIndexesAllSetValues" to ::executeInitialAddIndexesAllSetValues,
        "executeScanOnSetRefToAnyIndexRequestDoesNotDuplicateMultiValueObject" to ::executeScanOnSetRefToAnyIndexRequestDoesNotDuplicateMultiValueObject,
        "executeCursorPagingOnAnyValueIndexDoesNotDuplicateMultiValueObject" to ::executeCursorPagingOnAnyValueIndexDoesNotDuplicateMultiValueObject,
        "executeDescendingCursorPagingOnAnyValueIndexDoesNotDuplicateMultiValueObject" to ::executeDescendingCursorPagingOnAnyValueIndexDoesNotDuplicateMultiValueObject,
        "executeOrderedScanOnAnyValueIndexUsesMatchedStartKey" to ::executeOrderedScanOnAnyValueIndexUsesMatchedStartKey,
        "executeFilteredOrderedScanKeepsBoundaryWhenStartKeyDoesNotMatchFilter" to ::executeFilteredOrderedScanKeepsBoundaryWhenStartKeyDoesNotMatchFilter,
        "executeOrderedScanUpdatesUsesMatchedAnyValueSortingKey" to ::executeOrderedScanUpdatesUsesMatchedAnyValueSortingKey,
        "executeOrderedScanFlowUsesVisibleAnyValuePastStartBoundary" to ::executeOrderedScanFlowUsesVisibleAnyValuePastStartBoundary,
        "executeOrderedScanUpdatesFlowUsesVisibleAnyValuePastStartBoundary" to ::executeOrderedScanUpdatesFlowUsesVisibleAnyValuePastStartBoundary,
        "executeOrderedScanFlowUpdatesSortedValueWhenPositionStaysSame" to ::executeOrderedScanFlowUpdatesSortedValueWhenPositionStaysSame,
        "executeChangeUpdatesMapRefToAnyIndexRequest" to ::executeChangeUpdatesMapRefToAnyIndexRequest,
        "executeChangeUpdatesIncMapRefToAnyIndexRequest" to ::executeChangeUpdatesIncMapRefToAnyIndexRequest,
        "executeChangeUpdatesSetRefToAnyIndexRequest" to ::executeChangeUpdatesSetRefToAnyIndexRequest,
    )

    private val mapObjects = arrayOf(
        AnyValueMapIndexModel.create {
            name with "a"
            mapValues with mapOf("k1" to "m3")
        },
        AnyValueMapIndexModel.create {
            name with "b"
            mapValues with mapOf("k2" to "m1")
        },
        AnyValueMapIndexModel.create {
            name with "c"
            mapValues with mapOf("k3" to "m2")
        },
    )

    private val incMapObjects = arrayOf(
        AnyValueIncMapIndexModel.create {
            name with "a"
            incMapValues with mapOf(2u to "i2")
        },
        AnyValueIncMapIndexModel.create {
            name with "b"
            incMapValues with mapOf(3u to "i3")
        },
        AnyValueIncMapIndexModel.create {
            name with "c"
            incMapValues with mapOf(1u to "i1")
        },
    )

    private val setObjects = arrayOf(
        AnyValueSetIndexModel.create {
            name with "a"
            setValues with setOf("s3")
        },
        AnyValueSetIndexModel.create {
            name with "b"
            setValues with setOf("s1")
        },
        AnyValueSetIndexModel.create {
            name with "c"
            setValues with setOf("s2")
        },
    )

    override suspend fun initData() {
        dataStore.execute(AnyValueMapIndexModel.add(*mapObjects)).statuses.forEach { status ->
            mapKeys.add(assertStatusIs<AddSuccess<AnyValueMapIndexModel>>(status).key)
        }
        dataStore.execute(AnyValueIncMapIndexModel.add(*incMapObjects)).statuses.forEach { status ->
            incMapKeys.add(assertStatusIs<AddSuccess<AnyValueIncMapIndexModel>>(status).key)
        }
        dataStore.execute(AnyValueSetIndexModel.add(*setObjects)).statuses.forEach { status ->
            setKeys.add(assertStatusIs<AddSuccess<AnyValueSetIndexModel>>(status).key)
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            AnyValueMapIndexModel.delete(*mapKeys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        dataStore.execute(
            AnyValueIncMapIndexModel.delete(*incMapKeys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        dataStore.execute(
            AnyValueSetIndexModel.delete(*setKeys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        mapKeys.clear()
        incMapKeys.clear()
        setKeys.clear()
    }

    private suspend fun executeScanOnMapRefToAnyIndexRequest() {
        val scanResponse = dataStore.execute(
            AnyValueMapIndexModel.scan(
                order = AnyValueMapIndexModel { mapValues.refToAnyKey() }.ascending()
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("a", "b", "c")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeScanOnIncMapRefToAnyIndexRequest() {
        val scanResponse = dataStore.execute(
            AnyValueIncMapIndexModel.scan(
                order = AnyValueIncMapIndexModel { incMapValues.refToAnyKey() }.descending()
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("c", "a", "b")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeScanOnSetRefToAnyIndexRequest() {
        val scanResponse = dataStore.execute(
            AnyValueSetIndexModel.scan(
                order = AnyValueSetIndexModel { setValues.refToAny() }.ascending()
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("b", "c", "a")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeFilterOnMapRefToAnyIndexRequest() {
        val scanResponse = dataStore.execute(
            AnyValueMapIndexModel.scan(
                where = Equals(
                    AnyValueMapIndexModel { mapValues.refToAnyKey() } with "k3"
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("c")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeFilterOnIncMapRefToAnyIndexRequest() {
        val scanResponse = dataStore.execute(
            AnyValueIncMapIndexModel.scan(
                where = Equals(
                    AnyValueIncMapIndexModel { incMapValues.refToAnyKey() } with 2u
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("a")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeFilterOnSetRefToAnyIndexRequest() {
        val scanResponse = dataStore.execute(
            AnyValueSetIndexModel.scan(
                where = Equals(
                    AnyValueSetIndexModel { setValues.refToAny() } with "s2"
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("c")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeInitialAddIndexesAllMapKeys() {
        val status = dataStore.execute(
            AnyValueMapIndexModel.add(
                AnyValueMapIndexModel.create {
                    name with "d"
                    mapValues with mapOf(
                        "k4" to "m4",
                        "k0" to "m0"
                    )
                }
            )
        ).statuses.single()
        mapKeys += assertStatusIs<AddSuccess<AnyValueMapIndexModel>>(status).key

        val scanResponse = dataStore.execute(
            AnyValueMapIndexModel.scan(
                where = Equals(
                    AnyValueMapIndexModel { mapValues.refToAnyKey() } with "k0"
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("d")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeInitialAddIndexesAllSetValues() {
        val status = dataStore.execute(
            AnyValueSetIndexModel.add(
                AnyValueSetIndexModel.create {
                    name with "d"
                    setValues with setOf("s4", "s0")
                }
            )
        ).statuses.single()
        setKeys += assertStatusIs<AddSuccess<AnyValueSetIndexModel>>(status).key

        val scanResponse = dataStore.execute(
            AnyValueSetIndexModel.scan(
                where = Equals(
                    AnyValueSetIndexModel { setValues.refToAny() } with "s0"
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("d")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeScanOnSetRefToAnyIndexRequestDoesNotDuplicateMultiValueObject() {
        val status = dataStore.execute(
            AnyValueSetIndexModel.add(
                AnyValueSetIndexModel.create {
                    name with "d"
                    setValues with setOf("s4", "s0")
                }
            )
        ).statuses.single()
        setKeys += assertStatusIs<AddSuccess<AnyValueSetIndexModel>>(status).key

        val scanResponse = dataStore.execute(
            AnyValueSetIndexModel.scan(
                order = AnyValueSetIndexModel { setValues.refToAny() }.ascending()
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("d", "b", "c", "a")) {
            scanResponse.values.map { it.values { name } }
        }
    }

    private suspend fun executeCursorPagingOnAnyValueIndexDoesNotDuplicateMultiValueObject() {
        val status = dataStore.execute(
            AnyValueSetIndexModel.add(
                AnyValueSetIndexModel.create {
                    name with "d"
                    setValues with setOf("s4", "s0")
                }
            )
        ).statuses.single()
        setKeys += assertStatusIs<AddSuccess<AnyValueSetIndexModel>>(status).key

        val names = mutableListOf<String>()
        var cursor: ScanCursor? = null
        do {
            val page = dataStore.execute(
                AnyValueSetIndexModel.scan(
                    order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                    cursor = cursor,
                    limit = 1u,
                )
            )
            names += page.values.map { requireNotNull(it.values { name }) }
            cursor = page.nextCursor
        } while (cursor != null)

        expect(listOf("d", "b", "c", "a")) { names }
    }

    private suspend fun executeDescendingCursorPagingOnAnyValueIndexDoesNotDuplicateMultiValueObject() {
        val status = dataStore.execute(
            AnyValueSetIndexModel.add(
                AnyValueSetIndexModel.create {
                    name with "d"
                    setValues with setOf("s4", "s0")
                }
            )
        ).statuses.single()
        setKeys += assertStatusIs<AddSuccess<AnyValueSetIndexModel>>(status).key

        val names = mutableListOf<String>()
        var cursor: ScanCursor? = null
        do {
            val page = dataStore.execute(
                AnyValueSetIndexModel.scan(
                    order = AnyValueSetIndexModel { setValues.refToAny() }.descending(),
                    cursor = cursor,
                    limit = 1u,
                )
            )
            names += page.values.map { requireNotNull(it.values { name }) }
            cursor = page.nextCursor
        } while (cursor != null)

        expect(listOf("d", "a", "c", "b")) { names }
    }

    private suspend fun executeOrderedScanOnAnyValueIndexUsesMatchedStartKey() {
        val addedObject = AnyValueSetIndexModel.create {
            name with "d"
            setValues with setOf("s4", "s0")
        }
        val addedKey = assertStatusIs<AddSuccess<AnyValueSetIndexModel>>(
            dataStore.execute(AnyValueSetIndexModel.add(addedObject)).statuses.single()
        ).key
        setKeys += addedKey

        val scanResponse = dataStore.execute(
            AnyValueSetIndexModel.scan(
                order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                startKey = addedKey,
                includeStart = false
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("b", "c", "a", "d")) {
            scanResponse.values.map { it.values { name } }
        }
    }

    private suspend fun executeFilteredOrderedScanKeepsBoundaryWhenStartKeyDoesNotMatchFilter() {
        val scanResponse = dataStore.execute(
            AnyValueSetIndexModel.scan(
                where = Equals(
                    AnyValueSetIndexModel { setValues.refToAny() } with "s2"
                ),
                order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                startKey = setKeys.first(),
                includeStart = false
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("c")) {
            scanResponse.values.map { it.values { name } }
        }
    }

    private suspend fun executeOrderedScanUpdatesUsesMatchedAnyValueSortingKey() {
        val addedObject = AnyValueSetIndexModel.create {
            name with "e"
            setValues with setOf("s4", "s0")
        }
        val addedKey = assertStatusIs<AddSuccess<AnyValueSetIndexModel>>(
            dataStore.execute(AnyValueSetIndexModel.add(addedObject)).statuses.single()
        ).key
        setKeys += addedKey

        val scanResponse = dataStore.execute(
            AnyValueSetIndexModel.scanUpdates(
                order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                limit = 1u
            )
        )

        assertIs<OrderedKeysUpdate<AnyValueSetIndexModel>>(scanResponse.updates.first()).apply {
            expect(listOf(addedKey)) { keys }

            val actualSortingKey = assertNotNull(sortingKeys).single().bytes
            val expectedSortingKey = AnyValueSetIndexModel { setValues.refToAny() }
                .toStorageByteArraysForIndex(addedObject, addedKey.bytes)
                .minBy { it.toHexString() }

            expect(expectedSortingKey.toHexString()) { actualSortingKey.toHexString() }
        }
    }

    private suspend fun executeOrderedScanFlowUsesVisibleAnyValuePastStartBoundary() {
        val startKey = setKeys.first()

        updateListenerTester(
            dataStore = dataStore,
            request = AnyValueSetIndexModel.scan(
                order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                startKey = startKey,
                includeStart = false
            ),
            responseCount = 2
        ) { responses ->
            assertIs<InitialValuesUpdate<AnyValueSetIndexModel>>(responses[0].await()).apply {
                expect(emptyList()) { values.map { it.key } }
            }

            val addedKey = assertStatusIs<AddSuccess<AnyValueSetIndexModel>>(
                dataStore.execute(
                    AnyValueSetIndexModel.add(
                        AnyValueSetIndexModel.create {
                            name with "d"
                            setValues with setOf("s0", "s4")
                        }
                    )
                ).statuses.single()
            ).key
            setKeys += addedKey

            assertIs<AdditionUpdate<AnyValueSetIndexModel>>(responses[1].await()).apply {
                expect(addedKey) { key }
                expect("d") { values { name } }
                expect(0) { insertionIndex }
            }
        }
    }

    private suspend fun executeOrderedScanUpdatesFlowUsesVisibleAnyValuePastStartBoundary() {
        val startKey = setKeys.first()

        updateListenerTester(
            dataStore = dataStore,
            request = AnyValueSetIndexModel.scanUpdates(
                order = AnyValueSetIndexModel { setValues.refToAny() }.ascending(),
                startKey = startKey,
                includeStart = false
            ),
            responseCount = 2
        ) { responses ->
            assertIs<OrderedKeysUpdate<AnyValueSetIndexModel>>(responses[0].await()).apply {
                expect(emptyList()) { keys }
            }

            val addedKey = assertStatusIs<AddSuccess<AnyValueSetIndexModel>>(
                dataStore.execute(
                    AnyValueSetIndexModel.add(
                        AnyValueSetIndexModel.create {
                            name with "e"
                            setValues with setOf("s0", "s4")
                        }
                    )
                ).statuses.single()
            ).key
            setKeys += addedKey

            assertIs<AdditionUpdate<AnyValueSetIndexModel>>(responses[1].await()).apply {
                expect(addedKey) { key }
                expect("e") { values { name } }
                expect(0) { insertionIndex }
            }
        }
    }

    private suspend fun executeOrderedScanFlowUpdatesSortedValueWhenPositionStaysSame() = coroutineScope {
        val responses = Channel<IsUpdateResponse<AnyValueMapIndexModel>>(8)
        val listenJob = launch {
            dataStore.executeFlow(
                AnyValueMapIndexModel.scan(
                    order = AnyValueMapIndexModel { mapValues.refToAnyKey() }.ascending(),
                    limit = 2u
                )
            ).collect {
                responses.send(it)
            }
        }

        suspend fun receiveRealTimeResponse() = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5000) { responses.receive() }
        }

        suspend fun receiveRealTimeResponseOrNull(timeoutMs: Long) = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeoutOrNull(timeoutMs) { responses.receive() }
        }

        try {
            assertIs<InitialValuesUpdate<AnyValueMapIndexModel>>(receiveRealTimeResponse()).apply {
                expect(listOf(mapKeys[0], mapKeys[1])) { values.map { it.key } }
            }

            dataStore.execute(
                AnyValueMapIndexModel.change(
                    mapKeys[1].change(
                        Change(
                            AnyValueMapIndexModel { mapValues refAt "k2x" } with "m1"
                        )
                    )
                )
            ).statuses.forEach {
                assertStatusIs<ChangeSuccess<AnyValueMapIndexModel>>(it)
            }

            dataStore.execute(
                AnyValueMapIndexModel.change(
                    mapKeys[1].change(
                        Change(
                            AnyValueMapIndexModel { mapValues refAt "k2" } with null
                        )
                    )
                )
            ).statuses.forEach {
                assertStatusIs<ChangeSuccess<AnyValueMapIndexModel>>(it)
            }

            val changeUpdates = mutableListOf<ChangeUpdate<AnyValueMapIndexModel>>()
            while (true) {
                val update = receiveRealTimeResponseOrNull(250) ?: break
                changeUpdates += assertIs<ChangeUpdate<AnyValueMapIndexModel>>(update)
            }

            expect(true) {
                changeUpdates.any {
                    it.key == mapKeys[1] &&
                        it.index == 1 &&
                        it.changes.any { change -> change is IndexChange }
                }
            }

            val addedKey = assertStatusIs<AddSuccess<AnyValueMapIndexModel>>(
                dataStore.execute(
                    AnyValueMapIndexModel.add(
                        AnyValueMapIndexModel.create {
                            name with "f"
                            mapValues with mapOf("k2w" to "m4")
                        }
                    )
                ).statuses.single()
            ).key
            mapKeys += addedKey

            val firstUpdateAfterAdd = receiveRealTimeResponse()
            val secondUpdateAfterAdd = receiveRealTimeResponse()
            val updatesAfterAdd = listOf(firstUpdateAfterAdd, secondUpdateAfterAdd)

            assertIs<AdditionUpdate<AnyValueMapIndexModel>>(updatesAfterAdd.first { it is AdditionUpdate<*> }).apply {
                expect(addedKey) { key }
                expect("f") { values { name } }
                expect(1) { insertionIndex }
            }

            assertIs<RemovalUpdate<AnyValueMapIndexModel>>(updatesAfterAdd.first { it is RemovalUpdate<*> }).apply {
                expect(mapKeys[1]) { key }
                expect(NotInRange) { reason }
            }
        } finally {
            dataStore.closeAllListeners()
            listenJob.cancelAndJoin()
        }
    }

    private suspend fun executeChangeUpdatesMapRefToAnyIndexRequest() {
        val changeResponse = dataStore.execute(
            AnyValueMapIndexModel.change(
                mapKeys[0].change(
                    Change(
                        AnyValueMapIndexModel { mapValues refAt "k0" } with "m3"
                    )
                )
            )
        )
        changeResponse.statuses.forEach {
            assertStatusIs<ChangeSuccess<AnyValueMapIndexModel>>(it)
        }

        val scanResponse = dataStore.execute(
            AnyValueMapIndexModel.scan(
                where = Equals(
                    AnyValueMapIndexModel { mapValues.refToAnyKey() } with "k0"
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("a")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeChangeUpdatesIncMapRefToAnyIndexRequest() {
        val changeResponse = dataStore.execute(
            AnyValueIncMapIndexModel.change(
                incMapKeys[0].change(
                    Change(
                        AnyValueIncMapIndexModel { incMapValues refAt 4u } with "i2"
                    )
                )
            )
        )
        changeResponse.statuses.forEach {
            assertStatusIs<ChangeSuccess<AnyValueIncMapIndexModel>>(it)
        }

        val scanResponse = dataStore.execute(
            AnyValueIncMapIndexModel.scan(
                where = Equals(
                    AnyValueIncMapIndexModel { incMapValues.refToAnyKey() } with 4u
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("a")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

    private suspend fun executeChangeUpdatesSetRefToAnyIndexRequest() {
        val changeResponse = dataStore.execute(
            AnyValueSetIndexModel.change(
                setKeys[0].change(
                    SetChange(
                        AnyValueSetIndexModel { setValues::ref }.change(
                            addValues = setOf("s0")
                        )
                    )
                )
            )
        )
        changeResponse.statuses.forEach {
            assertStatusIs<ChangeSuccess<AnyValueSetIndexModel>>(it)
        }

        val scanResponse = dataStore.execute(
            AnyValueSetIndexModel.scan(
                where = Equals(
                    AnyValueSetIndexModel { setValues.refToAny() } with "s0"
                )
            )
        )

        assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
        expect(listOf("a")) {
            scanResponse.values.map { it.values { name } }.distinct()
        }
    }

}
