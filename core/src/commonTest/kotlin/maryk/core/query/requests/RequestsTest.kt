package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.objects.ObjectValues
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.requests.changeRequest
import maryk.test.requests.collectRequest
import maryk.test.requests.deleteRequest
import maryk.test.requests.getChangesRequest
import maryk.test.requests.getRequest
import maryk.test.requests.getVersionedChangesRequest
import maryk.test.requests.scanChangesRequest
import maryk.test.requests.scanRequest
import maryk.test.requests.scanVersionedChangesRequest
import maryk.test.shouldBe
import kotlin.test.Test

class RequestsTest {
    private val requests = Requests(
        addRequest,
        changeRequest,
        deleteRequest,
        getRequest,
        getChangesRequest,
        getVersionedChangesRequest,
        scanRequest,
        scanChangesRequest,
        scanVersionedChangesRequest,
        collectRequest
    )

    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.requests, Requests, { this.context }, { converted, original ->
            converted.requests.size shouldBe original.requests.size

            converted.requests.zip(original.requests).forEach {
                @Suppress("UNCHECKED_CAST")
                (it.first as TypedValue<RequestType, ObjectValues<*, *>>).value.toDataObject() shouldBe it.second
            }
        })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.requests, Requests, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.requests, Requests, { this.context }) shouldBe """
        - !Add
          dataModel: SimpleMarykModel
          objectsToAdd:
          - value: haha1
          - value: haha2
        - !Change
          dataModel: SimpleMarykModel
          objectChanges:
          - key: MYc6LBYcT38nWxoE1ahNxA
            changes:
          - key: lneV6ioyQL0vnbkLqwVw+A
            changes:
        - !Delete
          dataModel: SimpleMarykModel
          objectsToDelete: [B4CeT0fDRxYnEmSTQuLA2A, oDHjQh7GSDwyPX2kTUAniQ]
          hardDelete: true
        - !Get
          dataModel: SimpleMarykModel
          keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
          filterSoftDeleted: true
        - !GetChanges
          dataModel: SimpleMarykModel
          keys: [uBu6L+ARRCgpUuyks8f73g, CXTD69pnTdsytwq0yxPryA]
          toVersion: 3456
          filterSoftDeleted: true
          fromVersion: 1234
        - !GetVersionedChanges
          dataModel: SimpleMarykModel
          keys: [WWurg6ysTsozoMei/SurOw, awfbjYrVQ+cdXblfQKV10A]
          filterSoftDeleted: true
          fromVersion: 1234
          maxVersions: 1000
        - !Scan
          dataModel: SimpleMarykModel
          startKey: Zk6m4QpZQegUg5s13JVYlQ
          filterSoftDeleted: true
          limit: 100
        - !ScanChanges
          dataModel: SimpleMarykModel
          startKey: Zk6m4QpZQegUg5s13JVYlQ
          filterSoftDeleted: true
          limit: 100
          fromVersion: 1234
        - !ScanVersionedChanges
          dataModel: SimpleMarykModel
          startKey: Zk6m4QpZQegUg5s13JVYlQ
          filterSoftDeleted: true
          limit: 100
          fromVersion: 1234
          maxVersions: 1000
        - !Collect
          testName: !Get
            dataModel: SimpleMarykModel
            keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
            filterSoftDeleted: true

        """.trimIndent()
    }
}
