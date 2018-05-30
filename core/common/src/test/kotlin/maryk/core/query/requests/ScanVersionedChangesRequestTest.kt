package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.descending
import maryk.core.query.filters.Exists
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykObject.key("Zk6m4QpZQegUg5s13JVYlQ")

internal val scanVersionedChangesRequest = SimpleMarykObject.scanVersionedChanges(
    startKey = key1,
    fromVersion = 1234L.toUInt64()
)

internal val scanVersionedChangesMaxRequest = SimpleMarykObject.run {
    scanVersionedChanges(
        startKey = key1,
        filter = Exists(ref { value }),
        order = ref { value }.descending(),
        limit = 300.toUInt32(),
        toVersion = 2345L.toUInt64(),
        fromVersion = 1234L.toUInt64(),
        maxVersions = 10.toUInt32()
    )
}

class ScanVersionedChangesRequestTest {
    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
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
        dataModel: SimpleMarykObject
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filterSoftDeleted: true
        limit: 100
        fromVersion: 0x00000000000004d2
        maxVersions: 1000

        """.trimIndent()

        checkYamlConversion(scanVersionedChangesMaxRequest, ScanVersionedChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykObject
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filter: !Exists value
        order: !Desc value
        toVersion: 0x0000000000000929
        filterSoftDeleted: true
        limit: 300
        fromVersion: 0x00000000000004d2
        maxVersions: 10

        """.trimIndent()
    }
}
