package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.requests.changeRequest
import maryk.test.requests.collectRequest
import maryk.test.requests.deleteRequest
import maryk.test.requests.getChangesRequest
import maryk.test.requests.getRequest
import maryk.test.requests.scanChangesRequest
import maryk.test.requests.scanRequest
import kotlin.test.Test
import kotlin.test.expect

class RequestsTest {
    private val requests = Requests(
        addRequest,
        changeRequest,
        deleteRequest,
        getRequest,
        getChangesRequest,
        scanRequest,
        scanChangesRequest,
        collectRequest
    )

    private val context = RequestContext(mapOf(
        SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
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
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.requests, Requests.Model, { this.context }, ::requestsComparison)
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.requests, Requests.Model, { this.context }, ::requestsComparison)
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
              - key: lneV6ioyQL0vnbkLqwVw+A
                changes: []
            - !Delete
              from: SimpleMarykModel
              keys: [B4CeT0fDRxYnEmSTQuLA2A, oDHjQh7GSDwyPX2kTUAniQ]
              hardDelete: true
            - !Get
              from: SimpleMarykModel
              keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
              filterSoftDeleted: true
            - !GetChanges
              from: SimpleMarykModel
              keys: [WWurg6ysTsozoMei/SurOw, awfbjYrVQ+cdXblfQKV10A]
              filterSoftDeleted: true
              fromVersion: 0
              maxVersions: 1
            - !Scan
              from: SimpleMarykModel
              filterSoftDeleted: true
              limit: 100
              includeStart: true
            - !ScanChanges
              from: SimpleMarykModel
              filterSoftDeleted: true
              limit: 100
              includeStart: true
              fromVersion: 0
              maxVersions: 1
            - !Collect
              testName: !Get
                from: SimpleMarykModel
                keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
                filterSoftDeleted: true

            """.trimIndent()
        ) {
            checkYamlConversion(this.requests, Requests.Model, { this.context }, ::requestsComparison)
        }
    }
}
