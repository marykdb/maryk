package maryk.datastore.shared.updates

import kotlinx.coroutines.test.runTest
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.pairs.with
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.get
import maryk.core.query.responses.ValuesResponse
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class UpdateListenerCompatibilityTest {
    @Test
    fun currentValuesOverloadDelegatesToExistingImplementation() = runTest {
        val key = SimpleMarykModel.key(ByteArray(16))
        val values = SimpleMarykModel.create { value with "haha-current" }
        val expectedChange = Update.Change(SimpleMarykModel, key, 1uL, emptyList())
        var calls = 0
        // A custom listener implements only the contract published in 0.12.1.
        val listener = object : UpdateListener<SimpleMarykModel, IsFlowRequest<SimpleMarykModel, *>>(
            SimpleMarykModel.get(key),
            ValuesResponse(SimpleMarykModel, emptyList()),
        ) {
            override suspend fun process(update: Update<SimpleMarykModel>, dataStore: IsDataStore) = Unit

            override fun addValues(key: Key<SimpleMarykModel>, values: Values<SimpleMarykModel>): Int? = null

            override suspend fun changeOrder(
                change: Update.Change<SimpleMarykModel>,
                changedHandler: suspend (Int?, Boolean) -> Unit,
            ) {
                assertSame(expectedChange, change)
                calls++
                changedHandler(3, false)
            }
        }
        val handler: suspend (Int?, Boolean) -> Unit = { index, changed ->
            assertEquals(3, index)
            assertEquals(false, changed)
        }

        listener.changeOrder(expectedChange, handler)
        listener.changeOrder(expectedChange, values, handler)
        listener.changeOrder(expectedChange, null, handler)
        assertEquals(3, calls)
    }
}
