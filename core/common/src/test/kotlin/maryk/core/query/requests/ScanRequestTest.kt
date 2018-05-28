package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.ascending
import maryk.core.query.filters.Exists
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykObject.key("Zk6m4QpZQegUg5s13JVYlQ")

internal val scanRequest = SimpleMarykObject.scan(
    startKey = key1
)

internal val scanMaxRequest = SimpleMarykObject.run {
    scan(
        startKey = key1,
        filter = Exists(ref { value }),
        order = ref { value }.ascending(),
        limit = 200.toUInt32(),
        filterSoftDeleted = true,
        toVersion = 2345L.toUInt64()
    )
}

class ScanRequestTest {
    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(scanRequest, ScanRequest, this.context)
        checkProtoBufConversion(scanMaxRequest, ScanRequest, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(scanRequest, ScanRequest, this.context)
        checkJsonConversion(scanMaxRequest, ScanRequest, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(scanRequest, ScanRequest, this.context) shouldBe """
        dataModel: SimpleMarykObject
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filterSoftDeleted: true
        limit: 100

        """.trimIndent()

        checkYamlConversion(scanMaxRequest, ScanRequest, this.context) shouldBe """
        dataModel: SimpleMarykObject
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filter: !Exists value
        order: value
        toVersion: 0x0000000000000929
        filterSoftDeleted: true
        limit: 200

        """.trimIndent()
    }
}
