package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.ascending
import maryk.core.query.filters.Exists
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykObject.key("Zk6m4QpZQegUg5s13JVYlQ")

internal val scanSelectRequest = SimpleMarykObject.run {
    scanSelect(
        startKey = key1,
        select = props {
            RootPropRefGraph(
                value
            )
        }
    )
}

internal val scanSelectMaxRequest = SimpleMarykObject.run {
    scanSelect(
        startKey = key1,
        filter = Exists(ref { value }),
        order = ref { value }.ascending(),
        limit = 200.toUInt32(),
        filterSoftDeleted = true,
        toVersion = 2345L.toUInt64(),
        select = props {
            RootPropRefGraph(
                value
            )
        }
    )
}

class ScanSelectRequestTest {
    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to { SimpleMarykObject }
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(scanSelectRequest, ScanSelectRequest, { this.context })
        checkProtoBufConversion(scanSelectMaxRequest, ScanSelectRequest, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(scanSelectRequest, ScanSelectRequest, { this.context })
        checkJsonConversion(scanSelectMaxRequest, ScanSelectRequest, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(scanSelectRequest, ScanSelectRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykObject
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filterSoftDeleted: true
        limit: 100
        select:
        - value

        """.trimIndent()

        checkYamlConversion(scanSelectMaxRequest, ScanSelectRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykObject
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filter: !Exists value
        order: value
        toVersion: 2345
        filterSoftDeleted: true
        limit: 200
        select:
        - value

        """.trimIndent()
    }
}
