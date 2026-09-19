package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.requests.changeRequest
import maryk.test.requests.collectRequest
import maryk.test.requests.deleteRequest
import maryk.test.requests.getChangesRequest
import maryk.test.requests.getRequest
import maryk.test.requests.getUpdatesRequest
import maryk.test.requests.scanChangesRequest
import maryk.test.requests.scanRequest
import maryk.test.requests.scanUpdateHistoryRequest
import maryk.test.requests.scanUpdatesRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

class RequestsTest {
    private val requests = Requests(
        addRequest,
        changeRequest,
        deleteRequest,
        getRequest,
        getChangesRequest,
        getUpdatesRequest,
        scanRequest,
        scanChangesRequest,
        scanUpdatesRequest,
        scanUpdateHistoryRequest,
        collectRequest
    )

    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)
    ))

    private fun requestsComparison(converted: Requests, original: Requests) {
        expect(original.requests.size) { converted.requests.size }

//        converted.requests.zip(original.requests).forEach { pair ->
//            expect(pair.second) {
//                @Suppress("UNCHECKED_CAST")
//                ((pair.first as TypedValue<RequestType, *>).value as ObjectValues<IsTransportableRequest<*>, *>).toDataObject()
//            }
//            println(pair)
//        }
    }

    @Test
    fun rejectTooManyRequests() {
        assertFailsWith<RequestException> {
            Requests(List((MAX_REQUEST_BATCH_SIZE + 1u).toInt()) { getRequest })
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.requests, Requests, { this.context }, ::requestsComparison)
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.requests, Requests, { this.context }, ::requestsComparison)
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            - !Add
              to: SimpleMarykModel
              objects:
              - value: haha1
              - value: haha2
            - !Change
              to: SimpleMarykModel
              objects:
              - key: MYc6LBYcT38nWxoE1ahNxA
                changes: []
              - key: lneV6ioyQL0vnbkLqwVw-A
                changes: []
            - !Delete
              from: SimpleMarykModel
              keys: [B4CeT0fDRxYnEmSTQuLA2A, oDHjQh7GSDwyPX2kTUAniQ]
              hardDelete: true
            - !Get
              from: SimpleMarykModel
              keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX_mQHYCSEoLtfLSUQ]
              filterSoftDeleted: true
            - !GetChanges
              from: SimpleMarykModel
              keys: [WWurg6ysTsozoMei_SurOw, awfbjYrVQ-cdXblfQKV10A]
              filterSoftDeleted: true
              fromVersion: 0
              maxVersions: 1
            - !GetUpdates
              from: SimpleMarykModel
              keys: [WWurg6ysTsozoMei_SurOw, awfbjYrVQ-cdXblfQKV10A]
              filterSoftDeleted: true
              fromVersion: 0
              maxVersions: 1
            - !Scan
              from: SimpleMarykModel
              filterSoftDeleted: true
              limit: 100
              includeStart: true
              allowTableScan: false
            - !ScanChanges
              from: SimpleMarykModel
              filterSoftDeleted: true
              limit: 100
              includeStart: true
              fromVersion: 0
              maxVersions: 1
            - !ScanUpdates
              from: SimpleMarykModel
              filterSoftDeleted: true
              limit: 100
              includeStart: true
              fromVersion: 0
              maxVersions: 1
            - !ScanUpdateHistory
              from: SimpleMarykModel
              filterSoftDeleted: true
              limit: 100
              fromVersion: 0
            - !Collect
              testName: !Get
                from: SimpleMarykModel
                keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX_mQHYCSEoLtfLSUQ]
                filterSoftDeleted: true

            """.trimIndent()
        ) {
            checkYamlConversion(this.requests, Requests, { this.context }, ::requestsComparison)
        }
    }
}
