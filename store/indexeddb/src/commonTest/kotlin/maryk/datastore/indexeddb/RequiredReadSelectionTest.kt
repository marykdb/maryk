package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.indexeddb.processors.unserializableChangeLogMarker
import maryk.datastore.indexeddb.processors.createUpdateHistoryRowKey
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.requests.change
import maryk.core.query.changes.change
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.metric.ValueCount
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.orders.Order
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.indexeddb.processors.createChangeLogRowKey
import maryk.datastore.indexeddb.processors.requiredReadSelection
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object RequiredFieldsModel : RootDataModel<RequiredFieldsModel>(
    indexes = { listOf(Multiple(RequiredFieldsModel.group.ref(), RequiredFieldsModel.label.ref()), RequiredFieldsModel.label.ref()) },
) {
    val label by string(1u, final = true)
    val secret by string(2u, sensitive = true)
    val group by string(3u, final = true)
    val tags by list(4u, valueDefinition = StringDefinition(), required = false)
}

class RequiredReadSelectionTest {
    @Test
    fun filteredProjectionSkipsUnneededDecryptionAndRetainsPredicateAndAggregateFields() = runTest {
        installIndexedDbForTests()
        val provider = RequiredFieldsEncryptionProvider()
        val store = IndexedDbDataStore.open("required-fields-${Random.nextInt()}", mapOf(1u to RequiredFieldsModel),
            fieldEncryptionProvider = provider)
        try {
            val added = assertIs<AddSuccess<RequiredFieldsModel>>(store.execute(RequiredFieldsModel.add(
                RequiredFieldsModel.create { label with "visible"; secret with "private"; group with "g" },
            )).statuses.single())
            val select = RequiredFieldsModel.graph { listOf(label) }
            provider.decryptions = 0
            val projected = store.execute(RequiredFieldsModel.get(added.key, select = select,
                where = And(Exists(RequiredFieldsModel.group.ref()), Or(
                    Equals(RequiredFieldsModel.group.ref() with "g"), Not(Equals(RequiredFieldsModel.label.ref() with "visible"))))))
            assertEquals("visible", projected.values.single().values { label })
            assertNull(projected.values.single().values { group })
            assertNull(projected.values.single().values { secret })
            assertEquals(0, provider.decryptions)
            val filtered = store.execute(RequiredFieldsModel.get(added.key, select = select,
                where = Equals(RequiredFieldsModel.secret.ref() with "private")))
            assertEquals(1, provider.decryptions)
            assertEquals(projected.values, filtered.values)
            val aggregated = store.execute(RequiredFieldsModel.get(added.key, added.key, select = select,
                aggregations = Aggregations("count" to ValueCount(RequiredFieldsModel.secret.ref()))))
            assertEquals(2uL, (aggregated.aggregations!!.namedAggregations["count"] as ValueCountResponse<*>).value)
            assertEquals(2, aggregated.values.size)
            assertEquals(2, provider.decryptions)
            for (value in aggregated.values) {
                assertNull(value.values { secret })
                assertEquals(added.version, value.firstVersion)
                assertEquals(added.version, value.lastVersion)
            }
        } finally { store.close() }
    }

    @Test
    fun indexCursorRetainsHiddenOrderingFields() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("required-index-${Random.nextInt()}", mapOf(1u to RequiredFieldsModel),
            fieldEncryptionProvider = RequiredFieldsEncryptionProvider(), keepAllVersions = true)
        try {
            for (label in listOf("a", "b", "c")) {
                assertIs<AddSuccess<RequiredFieldsModel>>(store.execute(RequiredFieldsModel.add(
                    RequiredFieldsModel.create { this.label with label; group with "g"; secret with "payload" },
                )).statuses.single())
            }
            val first = store.execute(RequiredFieldsModel.scan(where = Equals(RequiredFieldsModel.group.ref() with "g"),
                order = Orders(RequiredFieldsModel.group.ref().ascending(), RequiredFieldsModel.label.ref().ascending()),
                select = RequiredFieldsModel.graph { listOf(label) }, limit = 2u))
            assertIs<FetchByIndexScan>(first.dataFetchType)
            assertEquals(listOf("a", "b"), first.values.map { it.values { label } })
            assertNull(first.values.first().values { group })
            val next = store.execute(RequiredFieldsModel.scan(where = Equals(RequiredFieldsModel.group.ref() with "g"),
                order = Orders(RequiredFieldsModel.group.ref().ascending(), RequiredFieldsModel.label.ref().ascending()),
                select = RequiredFieldsModel.graph { listOf(label) }, limit = 2u, cursor = assertNotNull(first.nextCursor)))
            assertEquals(listOf("c"), next.values.map { it.values { label } })
        } finally { store.close() }
    }

    @Test
    fun indexedWindowsKeepFilteringOrderingCursorsLegacyAndHistoricFallbacks() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("index-windows-${Random.nextInt()}", mapOf(1u to RequiredFieldsModel),
            fieldEncryptionProvider = RequiredFieldsEncryptionProvider(), keepAllVersions = true)
        try {
            val added = store.execute(RequiredFieldsModel.add(*(0 until 300).map { i ->
                RequiredFieldsModel.create { label with i.toString().padStart(3, '0'); group with "g"; secret with if (i < 260) "skip" else "keep" }
            }.toTypedArray())).statuses.map { assertIs<AddSuccess<RequiredFieldsModel>>(it) }
            val legacy = added[260].key
            val head = assertNotNull(store.byteStore.get("k:1", legacy.bytes))
            store.byteStore.put("k:1", legacy.bytes, head.copyOfRange(0, 17))
            store.byteStore.delete("k:1", added[270].key.bytes)
            assertIs<DeleteSuccess<RequiredFieldsModel>>(store.execute(RequiredFieldsModel.delete(added[265].key)).statuses.single())
            val filter = And(Equals(RequiredFieldsModel.group.ref() with "g"), Equals(RequiredFieldsModel.secret.ref() with "keep"))
            val select = RequiredFieldsModel.graph { listOf(label) }
            val ascending = Orders(RequiredFieldsModel.group.ref().ascending(), RequiredFieldsModel.label.ref().ascending())
            val expected = (260 until 300).filter { it != 265 && it != 270 }.map { it.toString().padStart(3, '0') }
            val first = store.execute(RequiredFieldsModel.scan(where = filter, order = ascending, select = select, limit = 20u))
            assertIs<FetchByIndexScan>(first.dataFetchType)
            assertEquals(expected.take(20), first.values.map { it.values { label } })
            val next = store.execute(RequiredFieldsModel.scan(where = filter, order = ascending, select = select,
                limit = 20u, cursor = assertNotNull(first.nextCursor)))
            assertEquals(expected.drop(20), next.values.map { it.values { label } })
            val descending = Orders(RequiredFieldsModel.group.ref().descending(), RequiredFieldsModel.label.ref().descending())
            val reverse = store.execute(RequiredFieldsModel.scan(where = filter, order = descending, select = select, limit = 20u))
            assertEquals(expected.reversed().take(20), reverse.values.map { it.values { label } })
            val reverseNext = store.execute(RequiredFieldsModel.scan(where = filter, order = descending, select = select,
                limit = 20u, cursor = assertNotNull(reverse.nextCursor)))
            assertEquals(expected.reversed().drop(20), reverseNext.values.map { it.values { label } })
            val historic = store.execute(RequiredFieldsModel.scan(where = filter, order = ascending, select = select,
                toVersion = added.last().version, limit = 20u))
            assertEquals((260 until 280).map { it.toString().padStart(3, '0') }, historic.values.map { it.values { label } })
        } finally { store.close() }
    }

    @Test
    fun batchedIndexHeadsStillRejectRestoredSchemaGenerationsBeforePublication() = runTest {
        installIndexedDbForTests()
        val provider = RequiredFieldsEncryptionProvider()
        val store = IndexedDbDataStore.open("index-window-schema-${Random.nextInt()}", mapOf(1u to RequiredFieldsModel),
            fieldEncryptionProvider = provider)
        try {
            assertIs<AddSuccess<RequiredFieldsModel>>(store.execute(RequiredFieldsModel.add(
                RequiredFieldsModel.create { label with "a"; group with "g"; secret with "private" },
            )).statuses.single())
            val options = assertNotNull(store.byteStore.get("meta", OptionsMetadataKey))
            provider.beforeDecrypt = {
                store.byteStore.put("meta", OptionsMetadataKey, options + byteArrayOf(99))
                store.byteStore.put("meta", OptionsMetadataKey, options)
            }
            val request = RequiredFieldsModel.scan(where = And(Equals(RequiredFieldsModel.group.ref() with "g"),
                Equals(RequiredFieldsModel.secret.ref() with "private")),
                order = Orders(RequiredFieldsModel.group.ref().ascending(), RequiredFieldsModel.label.ref().ascending()),
                select = RequiredFieldsModel.graph { listOf(label) }, limit = 2u)
            assertFailsWith<RequestException> { store.execute(request) }
            provider.beforeDecrypt = {}
            assertEquals("a", store.execute(request).values.single().values { label })
            provider.beforeDecrypt = {
                store.byteStore.put("meta", OptionsMetadataKey, options + byteArrayOf(99))
                store.byteStore.put("meta", OptionsMetadataKey, options)
            }
            val updates = RequiredFieldsModel.scanUpdates(where = request.where, order = request.order,
                select = request.select, limit = 2u)
            assertFailsWith<RequestException> { store.execute(updates) }
            provider.beforeDecrypt = {}
            assertEquals("a", store.execute(updates).updates.filterIsInstance<AdditionUpdate<RequiredFieldsModel>>().single().values { label })
        } finally { store.close() }
    }

    @Test
    fun updateScansReadHiddenPredicatesWithoutDecryptingUnselectedPayloads() = runTest {
        installIndexedDbForTests()
        for (history in listOf(false, true)) {
            val provider = RequiredFieldsEncryptionProvider()
            val store = IndexedDbDataStore.open("required-update-fields-$history-${Random.nextInt()}",
                mapOf(1u to RequiredFieldsModel), fieldEncryptionProvider = provider,
                keepAllVersions = history, keepUpdateHistoryIndex = history)
            try {
                val added = store.execute(RequiredFieldsModel.add(*(0 until 3).map { i ->
                    RequiredFieldsModel.create { label with "label-$i"; group with "g"; secret with "private-$i" }
                }.toTypedArray())).statuses.map { assertIs<AddSuccess<RequiredFieldsModel>>(it) }
                val select = RequiredFieldsModel.graph { listOf(label) }
                for (order in listOf(Order.ascending, RequiredFieldsModel.group.ref().ascending(), null)) {
                    for (toVersion in if (history) listOf(null, added.last().version) else listOf(null)) {
                        provider.fieldDecryptions = 0
                        val response = store.execute(RequiredFieldsModel.scanUpdates(
                            select = select, where = Equals(RequiredFieldsModel.group.ref() with "g"),
                            order = order, toVersion = toVersion, limit = 2u))
                        assertEquals(2, assertIs<OrderedKeysUpdate<RequiredFieldsModel>>(response.updates.first()).keys.size)
                        val additions = response.updates.filterIsInstance<AdditionUpdate<RequiredFieldsModel>>()
                        assertEquals(2, additions.size)
                        for (addition in additions) {
                            assertNotNull(addition.values { label })
                            assertNull(addition.values { group })
                            assertNull(addition.values { secret })
                        }
                        assertEquals(0, provider.fieldDecryptions, "history=$history order=$order toVersion=$toVersion")
                    }
                }
                provider.fieldDecryptions = 0
                val sensitive = store.execute(RequiredFieldsModel.scanUpdates(select = select,
                    where = Equals(RequiredFieldsModel.secret.ref() with "private-1"), order = Order.ascending))
                assertEquals(listOf(added[1].key), assertIs<OrderedKeysUpdate<RequiredFieldsModel>>(sensitive.updates.first()).keys)
                assertEquals(3, provider.fieldDecryptions)
            } finally { store.close() }
        }
    }

    @Test
    fun indexedUpdatePagesKeepLimitsOrderingLegacyMissingAndHistoricRows() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("update-index-pages-${Random.nextInt()}",
            mapOf(1u to RequiredFieldsModel), fieldEncryptionProvider = RequiredFieldsEncryptionProvider(),
            keepAllVersions = true)
        try {
            val added = store.execute(RequiredFieldsModel.add(*(0 until 300).map { i ->
                RequiredFieldsModel.create { label with i.toString().padStart(3, '0'); group with "g"; secret with if (i < 260) "skip" else "keep" }
            }.toTypedArray())).statuses.map { assertIs<AddSuccess<RequiredFieldsModel>>(it) }
            val legacy = added[260].key
            val head = assertNotNull(store.byteStore.get("k:1", legacy.bytes))
            store.byteStore.put("k:1", legacy.bytes, head.copyOfRange(0, 17))
            store.byteStore.delete("k:1", added[270].key.bytes)
            assertIs<DeleteSuccess<RequiredFieldsModel>>(store.execute(RequiredFieldsModel.delete(added[265].key)).statuses.single())
            val filter = And(Equals(RequiredFieldsModel.group.ref() with "g"), Equals(RequiredFieldsModel.secret.ref() with "keep"))
            val select = RequiredFieldsModel.graph { listOf(label) }
            for (reverse in listOf(false, true)) {
                val order = if (reverse) Orders(RequiredFieldsModel.group.ref().descending(), RequiredFieldsModel.label.ref().descending())
                    else Orders(RequiredFieldsModel.group.ref().ascending(), RequiredFieldsModel.label.ref().ascending())
                val expected = (260 until 300).filter { it != 265 && it != 270 }.let { if (reverse) it.reversed() else it }
                val first = store.execute(RequiredFieldsModel.scanUpdates(where = filter, order = order, select = select, limit = 20u))
                assertIs<FetchByIndexScan>(first.dataFetchType)
                val firstKeys = assertIs<OrderedKeysUpdate<RequiredFieldsModel>>(first.updates.first()).keys
                assertEquals(expected.take(20).map { added[it].key }, firstKeys)
                val additions = first.updates.filterIsInstance<AdditionUpdate<RequiredFieldsModel>>()
                assertEquals(expected.take(20).map { it.toString().padStart(3, '0') }, additions.map { it.values { label } })
                assertTrue(additions.all { it.values { secret } == null && it.values { group } == null })
                val next = store.execute(RequiredFieldsModel.scanUpdates(where = filter, order = order, select = select,
                    limit = 20u, startKey = firstKeys.last(), includeStart = false))
                assertEquals(expected.drop(20).map { added[it].key }, assertIs<OrderedKeysUpdate<RequiredFieldsModel>>(next.updates.first()).keys)
                val one = store.execute(RequiredFieldsModel.scanUpdates(where = filter, order = order, select = select, limit = 1u))
                assertEquals(firstKeys.take(1), assertIs<OrderedKeysUpdate<RequiredFieldsModel>>(one.updates.first()).keys)
            }
            val historic = store.execute(RequiredFieldsModel.scanUpdates(where = filter,
                order = Orders(RequiredFieldsModel.group.ref().ascending(), RequiredFieldsModel.label.ref().ascending()),
                select = select, toVersion = added.last().version, limit = 20u))
            assertEquals((260 until 280).map { added[it].key }, assertIs<OrderedKeysUpdate<RequiredFieldsModel>>(historic.updates.first()).keys)
        } finally { store.close() }
    }

    @Test
    fun indexedStartKeysReadOnlyOrderingDependenciesForValuesUpdatesAndChanges() = runTest {
        installIndexedDbForTests()
        val provider = RequiredFieldsEncryptionProvider()
        val store = IndexedDbDataStore.open("index-anchor-fields-${Random.nextInt()}",
            mapOf(1u to RequiredFieldsModel), fieldEncryptionProvider = provider, keepAllVersions = true)
        try {
            val added = store.execute(RequiredFieldsModel.add(*listOf("a", "b", "c").map { label ->
                RequiredFieldsModel.create { this.label with label; group with "g"; secret with "private-$label" }
            }.toTypedArray())).statuses.map { assertIs<AddSuccess<RequiredFieldsModel>>(it) }
            var anchorDecryptions = 0
            provider.fieldObserver = { context ->
                if (context.recordKey.contentEquals(added.first().key.bytes)) anchorDecryptions++
            }
            val order = Orders(RequiredFieldsModel.group.ref().ascending(), RequiredFieldsModel.label.ref().ascending())
            val select = RequiredFieldsModel.graph { listOf(label) }
            val filter = Equals(RequiredFieldsModel.group.ref() with "g")
            for (toVersion in listOf(null, added.last().version)) {
                val values = store.execute(RequiredFieldsModel.scan(where = filter, order = order, startKey = added.first().key,
                    includeStart = false, select = select, limit = 1u, toVersion = toVersion))
                assertEquals("b", values.values.single().values { label })
                assertNull(values.values.single().values { secret })
                assertEquals(0, anchorDecryptions, "Scan anchor toVersion=$toVersion")
                val updates = store.execute(RequiredFieldsModel.scanUpdates(where = filter, order = order, startKey = added.first().key,
                    includeStart = false, select = select, limit = 1u, toVersion = toVersion))
                assertEquals(listOf(added[1].key), assertIs<OrderedKeysUpdate<RequiredFieldsModel>>(updates.updates.first()).keys)
                assertEquals("b", updates.updates.filterIsInstance<AdditionUpdate<RequiredFieldsModel>>().single().values { label })
                assertEquals(0, anchorDecryptions, "ScanUpdates anchor toVersion=$toVersion")
                val changes = store.execute(RequiredFieldsModel.scanChanges(order = RequiredFieldsModel.label.ref().ascending(), startKey = added.first().key,
                    includeStart = false, select = select, limit = 1u, toVersion = toVersion))
                assertEquals(listOf(added[1].key), changes.changes.map { it.key })
                assertEquals(0, anchorDecryptions, "ScanChanges anchor toVersion=$toVersion")
                val full = store.execute(RequiredFieldsModel.scan(where = filter, order = order, startKey = added.first().key,
                    includeStart = false, limit = 1u, toVersion = toVersion))
                assertEquals("private-b", full.values.single().values { secret })
                assertEquals(0, anchorDecryptions, "Full Scan anchor toVersion=$toVersion")
            }
        } finally { store.close() }
    }

    @Test
    fun selectedChangeScansRetainHiddenPredicatesAndMissingLogCreationValues() = runTest {
        installIndexedDbForTests()
        val provider = RequiredFieldsEncryptionProvider()
        val store = IndexedDbDataStore.open("changes-required-fields-${Random.nextInt()}", mapOf(1u to RequiredFieldsModel),
            fieldEncryptionProvider = provider, keepAllVersions = true)
        try {
            val added = store.execute(RequiredFieldsModel.add(*listOf("a", "b", "c").map { label ->
                RequiredFieldsModel.create { this.label with label; group with if (label == "b") "other" else "g"; secret with "private-$label" }
            }.toTypedArray())).statuses.map { assertIs<AddSuccess<RequiredFieldsModel>>(it) }
            val select = RequiredFieldsModel.graph { listOf(label) }
            val filter = Equals(RequiredFieldsModel.group.ref() with "g")
            for (toVersion in listOf(null, added.last().version)) {
                for (order in listOf(null, RequiredFieldsModel.label.ref().ascending(), RequiredFieldsModel.label.ref().descending())) {
                    provider.fieldDecryptions = 0
                    val result = store.execute(RequiredFieldsModel.scanChanges(where = filter, order = order,
                        select = select, toVersion = toVersion, limit = 20u))
                    assertEquals(setOf(added[0].key, added[2].key), result.changes.map { it.key }.toSet())
                    val labels = result.changes.flatMap { row -> row.changes.flatMap { it.changes.filterIsInstance<Change>() } }
                        .flatMap { it.referenceValuePairs }.also { pairs ->
                            assertTrue(pairs.all { it.reference.completeName == "label" })
                        }.map { it.value }.toSet()
                    assertEquals(setOf("a", "c"), labels)
                    assertEquals(0, provider.fieldDecryptions)
                }
            }
            // The index fallback must reconstruct selected values even without a change-log row.
            store.byteStore.delete("c:1", createChangeLogRowKey(added[0].key.bytes, added[0].version))
            for (selection in listOf(select, RequiredFieldsModel.graph { emptyList() })) {
                val result = store.execute(RequiredFieldsModel.scanChanges(order = RequiredFieldsModel.label.ref().ascending(),
                    select = selection, limit = 1u))
                assertEquals(added[0].key, result.changes.single().key)
                val changes = result.changes.single().changes.single()
                assertEquals(added[0].version, changes.version)
                assertTrue(changes.changes.any { it is ObjectCreate })
                val pairs = changes.changes.filterIsInstance<Change>().flatMap { it.referenceValuePairs }
                assertEquals(if (selection.properties.isEmpty()) emptyList() else listOf("a"), pairs.map { it.value })
                assertTrue(pairs.all { it.reference.completeName == "label" })
            }
        } finally { store.close() }
    }

    @Test
    fun changeIndexWindowsKeepFilteredPagesLegacyHeadsAndSchemaGenerationChecks() = runTest {
        installIndexedDbForTests()
        val provider = RequiredFieldsEncryptionProvider()
        val store = IndexedDbDataStore.open("change-index-windows-${Random.nextInt()}", mapOf(1u to RequiredFieldsModel),
            fieldEncryptionProvider = provider, keepAllVersions = true)
        try {
            val added = store.execute(RequiredFieldsModel.add(*(0 until 300).map { i ->
                RequiredFieldsModel.create { label with i.toString().padStart(3, '0'); group with if (i < 260) "skip" else "keep"; secret with "private" }
            }.toTypedArray())).statuses.map { assertIs<AddSuccess<RequiredFieldsModel>>(it) }
            val head = assertNotNull(store.byteStore.get("k:1", added[260].key.bytes))
            store.byteStore.put("k:1", added[260].key.bytes, head.copyOfRange(0, 17))
            store.byteStore.delete("k:1", added[270].key.bytes)
            val deleted = assertIs<DeleteSuccess<RequiredFieldsModel>>(store.execute(RequiredFieldsModel.delete(added[265].key)).statuses.single())
            val filter = Or(Equals(RequiredFieldsModel.group.ref() with "keep"), Equals(RequiredFieldsModel.group.ref() with "never"))
            val select = RequiredFieldsModel.graph { listOf(label) }
            val expected = (260 until 300).filter { it != 265 && it != 270 }.map { added[it].key }
            for (descending in listOf(false, true)) {
                val order = if (descending) RequiredFieldsModel.label.ref().descending() else RequiredFieldsModel.label.ref().ascending()
                val keys = if (descending) expected.reversed() else expected
                val first = store.execute(RequiredFieldsModel.scanChanges(order = order, where = filter, select = select, limit = 20u))
                assertEquals(keys.take(20), first.changes.map { it.key })
                val next = store.execute(RequiredFieldsModel.scanChanges(order = order, where = filter, select = select, limit = 20u,
                    startKey = first.changes.last().key, includeStart = false))
                assertEquals(keys.drop(20), next.changes.map { it.key })
                val single = store.execute(RequiredFieldsModel.scanChanges(order = order, where = filter, select = select, limit = 1u))
                assertEquals(keys.take(1), single.changes.map { it.key })
            }
            val historic = store.execute(RequiredFieldsModel.scanChanges(order = RequiredFieldsModel.label.ref().ascending(),
                where = filter, select = select, limit = 20u, toVersion = added.last().version))
            assertEquals((260 until 280).map { added[it].key }, historic.changes.map { it.key })
            val caughtUp = store.execute(RequiredFieldsModel.scanChanges(order = RequiredFieldsModel.label.ref().ascending(),
                where = filter, select = select, limit = 20u, fromVersion = deleted.version + 1uL))
            assertTrue(caughtUp.changes.isEmpty())
            val options = assertNotNull(store.byteStore.get("meta", OptionsMetadataKey))
            provider.beforeDecrypt = {
                store.byteStore.put("meta", OptionsMetadataKey, options + byteArrayOf(99))
                store.byteStore.put("meta", OptionsMetadataKey, options)
            }
            assertFailsWith<RequestException> {
                store.execute(RequiredFieldsModel.scanChanges(order = RequiredFieldsModel.label.ref().ascending(),
                    select = RequiredFieldsModel.graph { listOf(label, secret) }, limit = 2u))
            }
            provider.beforeDecrypt = {}
        } finally { store.close() }
    }

    @Test
    fun filteredUpdateHistoryKeepsEventValuesCreationMarkersAndContinuationMasks() = runTest {
        installIndexedDbForTests()
        val provider = RequiredFieldsEncryptionProvider()
        val store = IndexedDbDataStore.open("history-required-fields-${Random.nextInt()}", mapOf(1u to RequiredFieldsModel),
            fieldEncryptionProvider = provider, keepAllVersions = true, keepUpdateHistoryIndex = true)
        try {
            val added = store.execute(RequiredFieldsModel.add(*listOf("a", "b").map { label ->
                RequiredFieldsModel.create { this.label with label; group with "g"; secret with "before" }
            }.toTypedArray())).statuses.map { assertIs<AddSuccess<RequiredFieldsModel>>(it) }
            assertIs<ChangeSuccess<RequiredFieldsModel>>(store.execute(RequiredFieldsModel.change(added[0].key.change(
                Change(RequiredFieldsModel.secret.ref() with "after"),
            ))).statuses.single())
            val select = RequiredFieldsModel.graph { listOf(label) }
            val filter = Equals(RequiredFieldsModel.group.ref() with "g")
            provider.fieldDecryptions = 0
            val response = store.execute(RequiredFieldsModel.scanUpdateHistory(where = filter, select = select, limit = 2u))
            val additions = response.updates.map { assertIs<AdditionUpdate<RequiredFieldsModel>>(it) }
            assertEquals(setOf("a", "b"), additions.map { it.values { label } }.toSet())
            additions.forEach { assertNull(it.values { secret }); assertNull(it.values { group }) }
            assertEquals(0, provider.fieldDecryptions)
            val before = store.execute(RequiredFieldsModel.scanUpdateHistory(
                where = Equals(RequiredFieldsModel.secret.ref() with "before"), select = select, limit = 2u))
            assertEquals(added.map { it.key }.toSet(), before.updates.map { (it as AdditionUpdate<RequiredFieldsModel>).key }.toSet())
            for (update in before.updates) {
                val addition = assertIs<AdditionUpdate<RequiredFieldsModel>>(update)
                assertEquals(added.single { it.key == addition.key }.version, addition.version)
                assertNull(addition.values { secret })
                assertNull(addition.values { group })
            }
            store.byteStore.put("uh:1", createUpdateHistoryRowKey(added[0].version, added[0].key.bytes), unserializableChangeLogMarker)
            provider.fieldDecryptions = 0
            val first = store.execute(RequiredFieldsModel.scanUpdateHistory(where = filter, select = select, limit = 1u))
            val next = store.execute(RequiredFieldsModel.scanUpdateHistory(where = filter, select = select, limit = 1u,
                cursor = assertNotNull(first.nextCursor)))
            val pages = (first.updates + next.updates).map { assertIs<AdditionUpdate<RequiredFieldsModel>>(it) }
            assertEquals(added.map { it.key }.toSet(), pages.map { it.key }.toSet())
            assertEquals(setOf("a", "b"), pages.map { it.values { label } }.toSet())
            pages.forEach { assertNull(it.values { secret }); assertNull(it.values { group }) }
            assertNull(next.nextCursor)
            assertEquals(0, provider.fieldDecryptions)
        } finally { store.close() }
    }

    @Test
    fun complexSelectionsAndDependenciesRetainFullDecoding() {
        val select = RequiredFieldsModel.graph { listOf(label) }
        assertNull(RequiredFieldsModel.requiredReadSelection(select, Exists(RequiredFieldsModel.tags.ref()), null))
        assertNull(RequiredFieldsModel.requiredReadSelection(RequiredFieldsModel.graph { listOf(tags) },
            Equals(RequiredFieldsModel.label.ref() with "a"), null))
    }
}

private class RequiredFieldsEncryptionProvider : ContextualFieldEncryptionProvider {
    var decryptions = 0
    var fieldDecryptions = 0
    var fieldObserver: (FieldEncryptionContext) -> Unit = {}
    var beforeDecrypt: suspend () -> Unit = {}
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int) = value.copyOfRange(offset, offset + length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray {
        beforeDecrypt()
        decryptions++
        return value.copyOfRange(offset, offset + length)
    }
    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int) = encrypt(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray {
        if (context.reference.contentEquals(RequiredFieldsModel.secret.ref().toStorageByteArray())) {
            fieldDecryptions++
            fieldObserver(context)
        }
        return decrypt(value, offset, length)
    }
}
