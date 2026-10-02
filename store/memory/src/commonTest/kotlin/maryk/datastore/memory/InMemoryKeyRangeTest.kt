package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.orders.Order
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InMemoryKeyRangeTest {
    @Test
    fun descendingExclusiveStartAtRangeEndDoesNotRepeatTheBoundary() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SequentialKeyModel))
        try {
            store.execute(SequentialKeyModel.add(*Array(3) { index ->
                SequentialKeyModel.create { id with (index + 1).toULong() }
            }))
            val filter = LessThanEquals(SequentialKeyModel.ref { id } with 3uL)
            val firstPage = store.execute(SequentialKeyModel.scan(
                where = filter, order = Order.descending, limit = 1u, allowTableScan = true,
            ))
            assertEquals(3uL, firstPage.values.single().values { id })
            val nextPage = store.execute(SequentialKeyModel.scan(
                where = filter, order = Order.descending,
                startKey = firstPage.values.single().key, includeStart = false, allowTableScan = true,
            ))
            assertEquals(listOf(2uL, 1uL), nextPage.values.map { it.values { id } })
            assertTrue(store.execute(SequentialKeyModel.scan(
                order = Order.descending, startKey = nextPage.values.last().key,
                includeStart = false, allowTableScan = true,
            )).values.isEmpty())
        } finally { store.close() }
    }

    @Test
    fun exclusiveMaximumBoundaryDoesNotRepeatTheLastRecord() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SequentialKeyModel))
        try {
            val added = assertIs<AddSuccess<SequentialKeyModel>>(store.execute(SequentialKeyModel.add(
                SequentialKeyModel.create { id with ULong.MAX_VALUE }
            )).statuses.single())
            for (filter in listOf(null, GreaterThan(SequentialKeyModel.ref { id } with 0uL))) {
                for (order in listOf(Order.ascending, Order.descending)) {
                    assertTrue(store.execute(SequentialKeyModel.scan(
                        where = filter,
                        order = order,
                        startKey = added.key,
                        includeStart = false,
                        allowTableScan = true,
                    )).values.isEmpty())
                }
            }
            assertTrue(store.execute(SequentialKeyModel.scan(
                where = GreaterThan(SequentialKeyModel.ref { id } with ULong.MAX_VALUE),
                allowTableScan = true,
            )).values.isEmpty())
        } finally {
            store.close()
        }
    }

    @Test
    fun exclusiveRangeIncludesItsImmediateSuccessor() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SequentialKeyModel))
        try {
            store.execute(SequentialKeyModel.add(*Array(3) { index ->
                SequentialKeyModel.create { id with (index + 1).toULong() }
            }))
            for (boundary in 0uL..3uL) {
                val result = store.execute(SequentialKeyModel.scan(
                    where = GreaterThan(SequentialKeyModel.ref { id } with boundary),
                    allowTableScan = true,
                ))
                assertEquals((1uL..3uL).filter { it > boundary }, result.values.map { it.values { id } })
            }
            val firstPage = store.execute(SequentialKeyModel.scan(
                where = GreaterThan(SequentialKeyModel.ref { id } with 0uL),
                limit = 1u,
                allowTableScan = true,
            ))
            val nextPage = store.execute(SequentialKeyModel.scan(
                where = GreaterThan(SequentialKeyModel.ref { id } with 0uL),
                startKey = firstPage.values.single().key,
                includeStart = false,
                allowTableScan = true,
            ))
            assertEquals(listOf(2uL, 3uL), nextPage.values.map { it.values { id } })
        } finally {
            store.close()
        }
    }
}

private object SequentialKeyModel : RootDataModel<SequentialKeyModel>(keyDefinition = { SequentialKeyModel.id.ref() }) {
    val id by number(1u, UInt64, final = true)
}
