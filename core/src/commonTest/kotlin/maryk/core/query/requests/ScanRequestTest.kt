package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.scanMaxRequest
import maryk.test.requests.scanOrdersRequest
import maryk.test.requests.scanRequest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.expect

class ScanRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
    ))

    @Test
    fun rejectLimitAboveMaximum() {
        assertFailsWith<RequestException> {
            SimpleMarykModel.scan(limit = MAX_SCAN_LIMIT + 1u)
        }
    }

    @Test
    fun rejectZeroLimit() {
        assertFailsWith<RequestException> {
            SimpleMarykModel.scan(limit = 0u)
        }
    }

    @Test
    fun cursorRoundTripsAndIsBoundToQuery() {
        val base = scanMaxRequest.copy(startKey = null, includeStart = true)
        val key = SimpleMarykModel.key("Zk6m4QpZQegUg5s13JVYlQ")
        val orderKey = byteArrayOf(1, 2, 3, 4)
        val cursor = base.createCursor(key, orderKey)
        val request = base.copy(cursor = cursor)

        val continuation = assertNotNull(request.resolveCursor())
        assertContentEquals(key.bytes, continuation.key.bytes)
        assertContentEquals(orderKey, continuation.orderKey?.bytes)
        checkProtoBufConversion(request, ScanRequest, { this.context })
        checkJsonConversion(request, ScanRequest, { this.context })
        checkYamlConversion(request, ScanRequest, { this.context })

        assertFailsWith<RequestException> {
            request.copy(order = scanOrdersRequest.order).resolveCursor()
        }
        assertFailsWith<RequestException> {
            request.copy(startKey = key)
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(scanRequest, ScanRequest, { this.context })
        checkProtoBufConversion(scanMaxRequest, ScanRequest, { this.context })
        checkProtoBufConversion(scanRequest.copy(allowTableScan = true), ScanRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(scanRequest, ScanRequest, { this.context })
        checkJsonConversion(scanMaxRequest, ScanRequest, { this.context })
        checkJsonConversion(scanOrdersRequest, ScanRequest, { this.context })
        assertTrue(
            checkJsonConversion(scanRequest.copy(allowTableScan = true), ScanRequest, { this.context }).contains("allowTableScan")
        )
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            from: SimpleMarykModel
            filterSoftDeleted: true
            limit: 100
            includeStart: true
            allowTableScan: false

            """.trimIndent()
        ) {
            checkYamlConversion(scanRequest, ScanRequest, { this.context })
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
            aggregations:
              totalValues: !ValueCount
                of: value
            order: value
            limit: 200
            includeStart: false
            allowTableScan: false

            """.trimIndent()
        ) {
            checkYamlConversion(scanMaxRequest, ScanRequest, { this.context })
        }

        expect(
            """
            from: SimpleMarykModel
            startKey: Zk6m4QpZQegUg5s13JVYlQ
            select:
            - value
            filterSoftDeleted: true
            order:
            - value
            - !Desc value
            limit: 100
            includeStart: true
            allowTableScan: false

            """.trimIndent()
        ) {
            checkYamlConversion(scanOrdersRequest, ScanRequest, { this.context })
        }

        assertTrue(
            checkYamlConversion(scanRequest.copy(allowTableScan = true), ScanRequest, { this.context }).contains("allowTableScan: true")
        )
    }
}
