package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.datastore.shared.executeTransaction
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionClockExhaustionTest {
    @Test
    fun transactionCannotWrapBelowAReplicatedCreationVersion() = runTest {
        for ((future, operations) in listOf(ULong.MAX_VALUE - 4uL to 4, ULong.MAX_VALUE - 2uL to 1)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel), keepAllVersions = true)
            try {
                val key = Key<SimpleMarykModel>(ByteArray(16) { 71 })
                val initial = SimpleMarykModel.create { value with "haha initial" }
                store.processUpdate(UpdateResponse(SimpleMarykModel,
                    AdditionUpdate(key, future, future, 0, false, initial)))
                val request = TransactionRequest("clock-range", (1..operations).map { ordinal ->
                    SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha $ordinal")))
                }, commit = true)

                repeat(2) {
                    val response = store.executeTransaction(request)
                    assertEquals(TransactionStatus.Aborted, response.status)
                    assertEquals(AtomicFailureCode.InvalidState, response.failure?.code)
                    val latest = store.execute(SimpleMarykModel.get(key)).values.single()
                    assertEquals(initial, latest.values)
                    assertEquals(future, latest.firstVersion)
                    assertEquals(future, latest.lastVersion)
                    assertEquals(initial, store.execute(SimpleMarykModel.get(key, toVersion = future)).values.single().values)
                }
            } finally { store.close() }
        }
    }
}
