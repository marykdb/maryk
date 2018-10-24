package maryk.core.query.requests

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.RequestContext
import maryk.core.query.ascending
import maryk.core.query.filters.Exists
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykModel.key("Zk6m4QpZQegUg5s13JVYlQ")

internal val scanRequest = SimpleMarykModel.run {
    scan(
        startKey = key1
    )
}

@Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")
internal val scanMaxRequest = SimpleMarykModel.run {
    scan(
        startKey = key1,
        filter = Exists(ref { value }),
        order = ref { value }.ascending(),
        limit = 200u,
        filterSoftDeleted = true,
        toVersion = 2345uL,
        select = props {
            RootPropRefGraph<SimpleMarykModel>(
                value
            )
        }
    )
}

class ScanSelectRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(scanRequest, ScanRequest, { this.context })
        checkProtoBufConversion(scanMaxRequest, ScanRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(scanRequest, ScanRequest, { this.context })
        checkJsonConversion(scanMaxRequest, ScanRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(scanRequest, ScanRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filterSoftDeleted: true
        limit: 100

        """.trimIndent()

        checkYamlConversion(scanMaxRequest, ScanRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        select:
        - value
        filter: !Exists value
        order: value
        toVersion: 2345
        filterSoftDeleted: true
        limit: 200

        """.trimIndent()
    }
}
