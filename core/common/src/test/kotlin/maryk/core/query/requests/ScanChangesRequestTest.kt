package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.Order
import maryk.core.query.filters.Exists
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykObject.key("Zk6m4QpZQegUg5s13JVYlQ")

internal val scanChangesRequest = SimpleMarykObject.scanChanges(
    startKey = key1,
    fromVersion = 1234L.toUInt64()
)

internal val scanChangeMaxRequest = SimpleMarykObject.scanChanges(
    startKey = key1,
    filter = Exists(SimpleMarykObject.ref { value }),
    order = Order(SimpleMarykObject.ref { value }),
    limit = 100.toUInt32(),
    filterSoftDeleted = true,
    toVersion = 2345L.toUInt64(),
    fromVersion = 1234L.toUInt64()
)

class ScanChangesRequestTest {


    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(scanChangesRequest, ScanChangesRequest, { this.context })
        checkProtoBufConversion(scanChangeMaxRequest, ScanChangesRequest, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(scanChangesRequest, ScanChangesRequest, { this.context })
        checkJsonConversion(scanChangeMaxRequest, ScanChangesRequest, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(scanChangesRequest, ScanChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykObject
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filterSoftDeleted: true
        limit: 100
        fromVersion: 0x00000000000004d2

        """.trimIndent()

        checkYamlConversion(scanChangeMaxRequest, ScanChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykObject
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filter: !Exists value
        order: value
        toVersion: 0x0000000000000929
        filterSoftDeleted: true
        limit: 100
        fromVersion: 0x00000000000004d2

        """.trimIndent()
    }
}
