package maryk.datastore.shared.updates

import maryk.core.models.key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.get
import maryk.core.query.responses.ValuesResponse
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
