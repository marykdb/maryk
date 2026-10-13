package maryk.datastore.test

import kotlinx.datetime.LocalDate
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.date
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.set
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Check
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.AtomicStatus
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.beginTransaction
import maryk.datastore.shared.executeAtomic
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

val modelContractModels = mapOf(1u to ModelContractModel)

class ModelContractRegressionCases(private val createStore: suspend (String) -> IsDataStore) {
    suspend fun inclusiveCompositeDateBoundaryPagesInBothDirections() = withStore("composite-date-boundary") { store ->
        val boundary = LocalDate(2026, 10, 13)
        var snapshot: ULong? = null
        for ((id, day) in listOf(1u to 12, 2u to 13, 3u to 13, 4u to 14, 5u to 13)) {
            assertIs<AddSuccess<ModelContractModel>>(store.execute(ModelContractModel.add(ModelContractModel.create {
                this.id with id
                anchor with if (id == 5u) 2u else 1u
                date with LocalDate(2026, 10, day)
                kind with id
            })).statuses.single()).also { snapshot = it.version }
        }
        val anchorFilter = Equals(ModelContractModel.ref { anchor } with 1u)
        for (descending in listOf(false, true)) for (toVersion in listOf(null, snapshot)) {
            val order = if (descending) Orders(ModelContractModel.ref { anchor }.descending(), ModelContractModel.ref { date }.descending(), ModelContractModel.ref { kind }.descending())
                else Orders(ModelContractModel.ref { anchor }.ascending(), ModelContractModel.ref { date }.ascending(), ModelContractModel.ref { kind }.ascending())
            for ((filter, ids) in listOf(
                And(anchorFilter, LessThanEquals(ModelContractModel.ref { date } with boundary)) to listOf(1u, 2u, 3u),
                And(anchorFilter, LessThan(ModelContractModel.ref { date } with boundary)) to listOf(1u),
                And(anchorFilter, GreaterThanEquals(ModelContractModel.ref { date } with boundary), Equals(ModelContractModel.ref { kind } with 2u)) to listOf(2u),
                And(anchorFilter, LessThanEquals(ModelContractModel.ref { date } with boundary), Equals(ModelContractModel.ref { kind } with 2u)) to listOf(2u),
                And(anchorFilter, GreaterThanEquals(ModelContractModel.ref { date } with boundary), LessThanEquals(ModelContractModel.ref { date } with boundary)) to listOf(2u, 3u),
            )) {
                val query = ModelContractModel.scan(where = filter, order = order, limit = 1u, toVersion = toVersion)
                var page = store.execute(query)
                val actual = mutableListOf<UInt>()
                var pages = 0
                while (true) {
                    assertTrue(++pages <= 10, "Cursor traversal must terminate")
                    actual += page.values.map { it.values[ModelContractModel.ref { id }]!! }
                    val cursor = page.nextCursor ?: break
                    page = store.execute(ModelContractModel.scan(where = filter, order = order, limit = 1u, cursor = cursor, toVersion = toVersion))
                }
                assertEquals(if (descending) ids.reversed() else ids, actual)
            }
        }
    }

    suspend fun variableLengthBoundariesKeepExactUpperAndStrictLowerSemantics() = withStore("string-boundaries") { store ->
        for ((id, text) in listOf(1u to "Jan", 2u to "Janne", 3u to "Jao", 4u to "Iam")) {
            assertIs<AddSuccess<ModelContractModel>>(store.execute(ModelContractModel.add(ModelContractModel.create {
                this.id with id; anchor with 1u; date with LocalDate(2026, 10, 13); kind with id; this.text with text
            })).statuses.single())
        }
        val anchorFilter = Equals(ModelContractModel.ref { anchor } with 1u)
        for (descending in listOf(false, true)) {
            val order = if (descending) Orders(ModelContractModel.ref { anchor }.descending(), ModelContractModel.ref { text }.descending(), ModelContractModel.ref { kind }.descending())
                else Orders(ModelContractModel.ref { anchor }.ascending(), ModelContractModel.ref { text }.ascending(), ModelContractModel.ref { kind }.ascending())
            for ((filter, ids) in listOf(
                And(anchorFilter, LessThanEquals(ModelContractModel.ref { text } with "Jan")) to listOf(4u, 1u),
                And(anchorFilter, GreaterThan(ModelContractModel.ref { text } with "Jan")) to listOf(2u, 3u),
            )) {
                var page = store.execute(ModelContractModel.scan(where = filter, order = order, limit = 1u))
                val actual = mutableListOf<UInt>()
                var pages = 0
                while (true) {
                    assertTrue(++pages <= 10, "Cursor traversal must terminate")
                    actual += page.values.map { it.values[ModelContractModel.ref { id }]!! }
                    val cursor = page.nextCursor ?: break
                    page = store.execute(ModelContractModel.scan(where = filter, order = order, limit = 1u, cursor = cursor))
                }
                assertEquals(if (descending) ids.reversed() else ids, actual)
            }
        }
    }

    suspend fun collectionReplacementSurvivesTransactionAndAtomicTransport() = withStore("collection-replacement") { store ->
        val added = assertIs<AddSuccess<ModelContractModel>>(store.execute(ModelContractModel.add(ModelContractModel.create {
            id with 1u; anchor with 1u; date with LocalDate(2026, 10, 13); kind with 1u
            tags with setOf("old"); numbers with listOf(0u); labels with mapOf("old" to "value")
        })).statuses.single())
        val tx = store.beginTransaction()
        tx.execute(ModelContractModel.change(added.key.change(Change(
            ModelContractModel.ref { tags } with setOf("first", "second"),
            ModelContractModel.ref { numbers } with listOf(1u, 2u),
            ModelContractModel.ref { labels } with mapOf("first" to "one", "second" to "two"),
        ))))
        val staged = tx.execute(ModelContractModel.get(added.key)).response.values.single().values
        assertEquals(setOf("first", "second"), staged[ModelContractModel.ref { tags }])
        assertEquals(listOf(1u, 2u), staged[ModelContractModel.ref { numbers }])
        assertEquals(mapOf("first" to "one", "second" to "two"), staged[ModelContractModel.ref { labels }])
        assertEquals(TransactionStatus.Committed, tx.commit().status)
        val committed = store.execute(ModelContractModel.get(added.key)).values.single().values
        assertEquals(staged[ModelContractModel.ref { tags }], committed[ModelContractModel.ref { tags }])
        assertEquals(staged[ModelContractModel.ref { numbers }], committed[ModelContractModel.ref { numbers }])
        assertEquals(staged[ModelContractModel.ref { labels }], committed[ModelContractModel.ref { labels }])
        val cleared = store.executeAtomic(AtomicRequest(
            changeSetId = "clear-collections",
            requests = listOf(ModelContractModel.change(added.key.change(Change(
                ModelContractModel.ref { tags } with emptySet<String>(),
                ModelContractModel.ref { numbers } with emptyList<UInt>(),
                ModelContractModel.ref { labels } with emptyMap<String, String>(),
            )))),
        ))
        assertEquals(AtomicStatus.Committed, cleared.status)
        val empty = store.execute(ModelContractModel.get(added.key)).values.single().values
        assertEquals(emptySet(), empty[ModelContractModel.ref { tags }]); assertEquals(emptyList(), empty[ModelContractModel.ref { numbers }]); assertEquals(emptyMap(), empty[ModelContractModel.ref { labels }])
        assertEquals(cleared, store.executeAtomic(AtomicRequest(changeSetId = "clear-collections", requests = listOf(ModelContractModel.change(added.key.change(Change(
            ModelContractModel.ref { tags } with emptySet<String>(), ModelContractModel.ref { numbers } with emptyList<UInt>(), ModelContractModel.ref { labels } with emptyMap<String, String>(),
        )))))))
        val removed = store.beginTransaction()
        removed.execute(ModelContractModel.change(added.key.change(Change(ModelContractModel.ref { tags } with null, ModelContractModel.ref { numbers } with null, ModelContractModel.ref { labels } with null))))
        assertEquals(TransactionStatus.Committed, removed.commit().status)
        val absent = store.execute(ModelContractModel.get(added.key)).values.single().values
        assertEquals(null, absent[ModelContractModel.ref { tags }]); assertEquals(null, absent[ModelContractModel.ref { numbers }]); assertEquals(null, absent[ModelContractModel.ref { labels }])
    }

    suspend fun collectionChecksUseCompleteValuesAndRejectMissingProperties() = withStore("collection-checks") { store ->
        val added = assertIs<AddSuccess<ModelContractModel>>(store.execute(ModelContractModel.add(ModelContractModel.create {
            id with 1u; anchor with 1u; date with LocalDate(2026, 10, 13); kind with 1u
            tags with setOf("first", "second"); numbers with listOf(1u, 2u); labels with mapOf("first" to "one", "second" to "two")
        })).statuses.single())
        assertIs<ChangeSuccess<ModelContractModel>>(store.execute(ModelContractModel.change(added.key.change(Check(
            ModelContractModel.ref { tags } with setOf("first", "second"), ModelContractModel.ref { numbers } with listOf(1u, 2u),
            ModelContractModel.ref { labels } with mapOf("first" to "one", "second" to "two"),
        )))).statuses.single())
        assertIs<ChangeSuccess<ModelContractModel>>(store.execute(ModelContractModel.change(added.key.change(
            Change(ModelContractModel.ref { tags } with setOf("staged"), ModelContractModel.ref { kind } with 2u),
            Check(ModelContractModel.ref { tags } with setOf("staged"), ModelContractModel.ref { kind } with 2u),
        ))).statuses.single())
        assertIs<ValidationFail<ModelContractModel>>(store.execute(ModelContractModel.change(added.key.change(
            Change(ModelContractModel.ref { tags } with setOf("discarded")), Check(ModelContractModel.ref { tags } with setOf("staged")),
        ))).statuses.single())
        assertEquals(setOf("staged"), store.execute(ModelContractModel.get(added.key)).values.single().values[ModelContractModel.ref { tags }])
        assertIs<ValidationFail<ModelContractModel>>(store.execute(ModelContractModel.change(added.key.change(
            Check(ModelContractModel.ref { text } with "missing"), Change(ModelContractModel.ref { kind } with 9u),
        ))).statuses.single())
        assertEquals(2u, store.execute(ModelContractModel.get(added.key)).values.single().values[ModelContractModel.ref { kind }])
        assertIs<ChangeSuccess<ModelContractModel>>(store.execute(ModelContractModel.change(added.key.change(Change(
            ModelContractModel.ref { tags } with emptySet<String>(), ModelContractModel.ref { numbers } with emptyList<UInt>(), ModelContractModel.ref { labels } with emptyMap<String, String>(),
        )))).statuses.single())
        assertIs<ChangeSuccess<ModelContractModel>>(store.execute(ModelContractModel.change(added.key.change(Check(
            ModelContractModel.ref { tags } with emptySet<String>(), ModelContractModel.ref { numbers } with emptyList<UInt>(), ModelContractModel.ref { labels } with emptyMap<String, String>(),
        )))).statuses.single())
    }

    private suspend fun withStore(name: String, block: suspend (IsDataStore) -> Unit) {
        val store = createStore(name)
        try { block(store) } finally { store.close() }
    }
}

object ModelContractModel : RootDataModel<ModelContractModel>(
    keyDefinition = { ModelContractModel.id.ref() },
    indexes = { listOf(Multiple(ModelContractModel.anchor.ref(), ModelContractModel.date.ref(), ModelContractModel.kind.ref()), Multiple(ModelContractModel.anchor.ref(), ModelContractModel.text.ref(), ModelContractModel.kind.ref())) },
) {
    val id by number(1u, type = UInt32, final = true)
    val anchor by number(2u, type = UInt32)
    val date by date(3u)
    val kind by number(4u, type = UInt32)
    val tags by set(5u, required = false, valueDefinition = StringDefinition())
    val numbers by list(6u, required = false, valueDefinition = NumberDefinition(type = UInt32))
    val text by string(8u, required = false)
    val labels by map(7u, required = false, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}
