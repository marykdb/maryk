package maryk.datastore.shared.updates

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import maryk.core.models.key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.Change
import maryk.core.query.pairs.with
import maryk.core.query.requests.get
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class UpdateListenerForGetTest {
    @Test
    fun addValuesRespectsRequestKeyOrderWhenUnsorted() {
        val firstKey = SimpleMarykModel.key(ByteArray(16) { 2 })
        val secondKey = SimpleMarykModel.key(ByteArray(16) { 1 })
        val request = SimpleMarykModel.get(firstKey, secondKey)

        val values = SimpleMarykModel.create {
            value with "value"
        }

        val response = ValuesResponse(
            dataModel = SimpleMarykModel,
            values = listOf(
                ValuesWithMetaData(
                    key = firstKey,
                    values = values,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false,
                ),
                ValuesWithMetaData(
                    key = secondKey,
                    values = values,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false,
                ),
            ),
        )

        val listener = UpdateListenerForGet(request, response)

        assertEquals(0, listener.addValues(firstKey, values))
        assertEquals(1, listener.addValues(secondKey, values))
    }

    @Test
    fun addValuesReturnsNullForUnknownKey() {
        val key = SimpleMarykModel.key(ByteArray(16) { 3 })
        val request = SimpleMarykModel.get(key)
        val values = SimpleMarykModel.create {
            value with "value"
        }
        val response = ValuesResponse(
            dataModel = SimpleMarykModel,
            values = listOf(
                ValuesWithMetaData(
                    key = key,
                    values = values,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false,
                ),
            ),
        )
        val listener = UpdateListenerForGet(request, response)
        val missingKey = SimpleMarykModel.key(ByteArray(16) { 4 })

        assertNull(listener.addValues(missingKey, values))
    }

    @Test
    fun updateProcessedBeforeCollectionIsBuffered() = runTest {
        val key = SimpleMarykModel.key(ByteArray(16))
        val initialValues = SimpleMarykModel.create {
            value with "initial"
        }
        val request = SimpleMarykModel.get(key)
        val response = ValuesResponse(
            dataModel = SimpleMarykModel,
            values = listOf(
                ValuesWithMetaData(
                    key = key,
                    values = initialValues,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false,
                ),
            ),
        )
        val listener = UpdateListenerForGet(request, response)
        val change = Change(SimpleMarykModel { value::ref } with "changed")

        listener.process(
            Update.Change(SimpleMarykModel, key, 2uL, listOf(change)),
            ProcessUpdateActorTest.TestDataStore
        )

        val updates = withTimeout(200.milliseconds) {
            listener.getFlow().take(2).toList()
        }

        assertIs<InitialValuesUpdate<*>>(updates[0])
        assertIs<ChangeUpdate<*>>(updates[1]).apply {
            assertEquals(key, this.key)
            assertEquals(listOf(change), changes)
        }
    }
}
