package maryk.core.query.requests

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.RequestContext
import maryk.core.query.descending
import maryk.core.query.filters.Exists
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykModel.key("Zk6m4QpZQegUg5s13JVYlQ")

internal val scanVersionedChangesRequest = SimpleMarykModel.scanVersionedChanges(
    startKey = key1,
    fromVersion = 1234L.toUInt64()
)

internal val scanVersionedChangesMaxRequest = SimpleMarykModel.run {
    scanVersionedChanges(
        startKey = key1,
        filter = Exists(ref { value }),
        order = ref { value }.descending(),
        limit = 300.toUInt32(),
        toVersion = 2345L.toUInt64(),
        fromVersion = 1234L.toUInt64(),
        maxVersions = 10.toUInt32(),
        select = SimpleMarykModel.props {
            RootPropRefGraph<SimpleMarykModel>(
                value
            )
        }
    )
}

class ScanVersionedChangesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(scanVersionedChangesRequest, ScanVersionedChangesRequest, { this.context })
        checkProtoBufConversion(scanVersionedChangesMaxRequest, ScanVersionedChangesRequest, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(scanVersionedChangesRequest, ScanVersionedChangesRequest, { this.context })
        checkJsonConversion(scanVersionedChangesMaxRequest, ScanVersionedChangesRequest, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(scanVersionedChangesRequest, ScanVersionedChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filterSoftDeleted: true
        limit: 100
        fromVersion: 1234
        maxVersions: 1000

        """.trimIndent()

        checkYamlConversion(scanVersionedChangesMaxRequest, ScanVersionedChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        select:
        - value
        filter: !Exists value
        order: !Desc value
        toVersion: 2345
        filterSoftDeleted: true
        limit: 300
        fromVersion: 1234
        maxVersions: 10

        """.trimIndent()
    }
}
