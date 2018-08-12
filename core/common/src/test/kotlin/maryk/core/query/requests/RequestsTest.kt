package maryk.core.query.requests

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.RequestContext
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
        SimpleMarykModel.name to { SimpleMarykModel }
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.requests, Requests, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.requests, Requests, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
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
