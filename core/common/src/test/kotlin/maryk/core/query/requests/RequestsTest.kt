package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DataModelPropertyContext
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
        scanVersionedChangesRequest
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to { SimpleMarykObject }
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
          dataModel: SimpleMarykObject
          objectsToAdd:
          - value: haha1
          - value: haha2
        - !Change
          dataModel: SimpleMarykObject
          objectChanges:
          - key: MYc6LBYcT38nWxoE1ahNxA
            changes:
          - key: lneV6ioyQL0vnbkLqwVw+A
            changes:
        - !Delete
          dataModel: SimpleMarykObject
          objectsToDelete: [B4CeT0fDRxYnEmSTQuLA2A, oDHjQh7GSDwyPX2kTUAniQ]
          hardDelete: true
        - !Get
          dataModel: SimpleMarykObject
          keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
          filterSoftDeleted: true
        - !GetChanges
          dataModel: SimpleMarykObject
          keys: [uBu6L+ARRCgpUuyks8f73g, CXTD69pnTdsytwq0yxPryA]
          toVersion: 0x0000000000000d80
          filterSoftDeleted: true
          fromVersion: 0x00000000000004d2
        - !GetVersionedChanges
          dataModel: SimpleMarykObject
          keys: [WWurg6ysTsozoMei/SurOw, awfbjYrVQ+cdXblfQKV10A]
          filterSoftDeleted: true
          fromVersion: 0x00000000000004d2
          maxVersions: 1000
        - !Scan
          dataModel: SimpleMarykObject
          startKey: Zk6m4QpZQegUg5s13JVYlQ
          filterSoftDeleted: true
          limit: 100
        - !ScanChanges
          dataModel: SimpleMarykObject
          startKey: Zk6m4QpZQegUg5s13JVYlQ
          filterSoftDeleted: true
          limit: 100
          fromVersion: 0x00000000000004d2
        - !ScanVersionedChanges
          dataModel: SimpleMarykObject
          startKey: Zk6m4QpZQegUg5s13JVYlQ
          filterSoftDeleted: true
          limit: 100
          fromVersion: 0x00000000000004d2
          maxVersions: 1000

        """.trimIndent()
    }
}
