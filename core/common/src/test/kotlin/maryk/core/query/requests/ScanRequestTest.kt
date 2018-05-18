package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.ascending
import maryk.core.query.filters.exists
import maryk.test.shouldBe
import kotlin.test.Test

class ScanRequestTest {
    private val key1 = SimpleMarykObject.key("Zk6m4QpZQegUg5s13JVYlQ")

    private val scanRequest = SimpleMarykObject.scan(
        startKey = key1
    )

    private val scanMaxRequest = SimpleMarykObject.run {
        scan(
            startKey = key1,
            filter = ref { value }.exists(),
            order = ref { value }.ascending(),
            limit = 200.toUInt32(),
            filterSoftDeleted = true,
            toVersion = 2345L.toUInt64()
        )
    }

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.scanRequest, ScanRequest, this.context)
        checkProtoBufConversion(this.scanMaxRequest, ScanRequest, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.scanRequest, ScanRequest, this.context)
        checkJsonConversion(this.scanMaxRequest, ScanRequest, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.scanRequest, ScanRequest, this.context) shouldBe """
        dataModel: SimpleMarykObject
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filterSoftDeleted: true
        limit: 100

        """.trimIndent()

        checkYamlConversion(this.scanMaxRequest, ScanRequest, this.context) shouldBe """
        dataModel: SimpleMarykObject
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filter: !Exists value
        order:
          propertyReference: value
          direction: ASC
        toVersion: 0x0000000000000929
        filterSoftDeleted: true
        limit: 200

        """.trimIndent()
    }
}
