package maryk.core.query.requests

import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Key
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RequestBoundaryValidationTest {
    private val invalidKey = Key<SimpleMarykModel>(ByteArray(SimpleMarykModel.Meta.keyByteSize - 1))

    @Test
    fun rejectsWrongSizedKeysAtEveryRequestIngress() {
        assertFailsWith<RequestException> { SimpleMarykModel.get(invalidKey) }
        assertFailsWith<RequestException> { SimpleMarykModel.delete(invalidKey) }
        assertFailsWith<RequestException> { SimpleMarykModel.getChanges(invalidKey) }
        assertFailsWith<RequestException> { SimpleMarykModel.getUpdates(invalidKey) }
        assertFailsWith<RequestException> { SimpleMarykModel.add(invalidKey to addRequest.objects.first()) }
        assertFailsWith<RequestException> { SimpleMarykModel.scan(startKey = invalidKey) }
        assertFailsWith<RequestException> { SimpleMarykModel.scanChanges(startKey = invalidKey) }
        assertFailsWith<RequestException> { SimpleMarykModel.scanUpdates(startKey = invalidKey) }
        assertFailsWith<RequestException> { SimpleMarykModel.scanUpdates(orderedKeys = listOf(invalidKey)) }
    }

    @Test
    fun rejectsHistoryBoundsAtRequestConstruction() {
        listOf(1001u, UInt.MAX_VALUE).forEach { maxVersions ->
            assertFailsWith<RequestException> { SimpleMarykModel.getChanges(maxVersions = maxVersions) }
            assertFailsWith<RequestException> { SimpleMarykModel.getUpdates(maxVersions = maxVersions) }
            assertFailsWith<RequestException> { SimpleMarykModel.scanChanges(maxVersions = maxVersions) }
            assertFailsWith<RequestException> { SimpleMarykModel.scanUpdates(maxVersions = maxVersions) }
        }
    }
}
