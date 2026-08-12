package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.scanUpdatesMaxRequest
import maryk.test.requests.scanUpdatesRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.expect

class ScanUpdatesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
    ))

    @Test
    fun rejectLimitAboveMaximum() {
        assertFailsWith<RequestException> {
            SimpleMarykModel.scanUpdates(limit = MAX_SCAN_LIMIT + 1u)
        }
    }

    @Test
    fun rejectZeroLimit() {
        assertFailsWith<RequestException> {
            SimpleMarykModel.scanUpdates(limit = 0u)
        }
    }

    @Test
    fun cacheOrderedKeyMembershipForMaximumRequestSize() {
        val keys: List<Key<SimpleMarykModel>> = List(MAX_SCAN_LIMIT.toInt()) { index ->
            SimpleMarykModel.key(ByteArray(SimpleMarykModel.Meta.keyByteSize) { byteIndex ->
                (index shr (byteIndex * 8)).toByte()
            })
        }
        val request = SimpleMarykModel.scanUpdates(orderedKeys = keys)

        assertEquals(keys.size, request.orderedKeysSet?.size)
        assertTrue(keys.first() in request.orderedKeysSet.orEmpty())
        assertTrue(keys.last() in request.orderedKeysSet.orEmpty())
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(scanUpdatesRequest, ScanUpdatesRequest, { this.context })
        checkProtoBufConversion(scanUpdatesMaxRequest, ScanUpdatesRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(scanUpdatesRequest, ScanUpdatesRequest, { this.context })
        checkJsonConversion(scanUpdatesMaxRequest, ScanUpdatesRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            from: SimpleMarykModel
            filterSoftDeleted: true
            limit: 100
            includeStart: true
            fromVersion: 0
            maxVersions: 1

            """.trimIndent()
        ) {
            checkYamlConversion(scanUpdatesRequest, ScanUpdatesRequest, { this.context })
        }

        expect(
            """
            from: SimpleMarykModel
            startKey: Zk6m4QpZQegUg5s13JVYlQ
            select:
            - value
            where: !Exists value
            toVersion: 2345
            filterSoftDeleted: true
            order: !Desc value
            limit: 300
            includeStart: false
            fromVersion: 1234
            maxVersions: 10
            orderedKeys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX_mQHYCSEoLtfLSUQ]

            """.trimIndent()
        ) {
            checkYamlConversion(scanUpdatesMaxRequest, ScanUpdatesRequest, { this.context })
        }
    }
}
