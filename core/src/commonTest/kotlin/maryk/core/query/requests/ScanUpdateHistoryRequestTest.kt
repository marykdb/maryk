package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.scanUpdateHistoryMaxRequest
import maryk.test.requests.scanUpdateHistoryRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

class ScanUpdateHistoryRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
    ))

    @Test
    fun rejectLimitAboveMaximum() {
        assertFailsWith<RequestException> {
            SimpleMarykModel.scanUpdateHistory(limit = MAX_SCAN_LIMIT + 1u)
        }
    }

    @Test
    fun rejectZeroLimit() {
        assertFailsWith<RequestException> {
            SimpleMarykModel.scanUpdateHistory(limit = 0u)
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(scanUpdateHistoryRequest, ScanUpdateHistoryRequest, { this.context })
        checkProtoBufConversion(scanUpdateHistoryMaxRequest, ScanUpdateHistoryRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(scanUpdateHistoryRequest, ScanUpdateHistoryRequest, { this.context })
        checkJsonConversion(scanUpdateHistoryMaxRequest, ScanUpdateHistoryRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            from: SimpleMarykModel
            filterSoftDeleted: true
            limit: 100
            fromVersion: 0

            """.trimIndent()
        ) {
            checkYamlConversion(scanUpdateHistoryRequest, ScanUpdateHistoryRequest, { this.context })
        }

        expect(
            """
            from: SimpleMarykModel
            select:
            - value
            where: !Exists value
            toVersion: 2345
            filterSoftDeleted: true
            limit: 300
            fromVersion: 1234

            """.trimIndent()
        ) {
            checkYamlConversion(scanUpdateHistoryMaxRequest, ScanUpdateHistoryRequest, { this.context })
        }
    }
}
