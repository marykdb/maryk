package maryk.core.query.requests

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.RequestContext
import maryk.core.query.ascending
import maryk.core.query.filters.Exists
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykModel.key("uBu6L+ARRCgpUuyks8f73g")
private val key2 = SimpleMarykModel.key("CXTD69pnTdsytwq0yxPryA")

internal val getChangesRequest = SimpleMarykModel.getChanges(
    key1,
    key2,
    fromVersion = 1234L.toUInt64(),
    toVersion = 3456L.toUInt64()
)

internal val getChangesMaxRequest = SimpleMarykModel.run {
    getChanges(
        key1,
        key2,
        filter = Exists(ref { value }),
        order = ref { value }.ascending(),
        fromVersion = 1234L.toUInt64(),
        toVersion = 3456L.toUInt64(),
        filterSoftDeleted = true,
        select = SimpleMarykModel.props {
            RootPropRefGraph<SimpleMarykModel>(
                value
            )
        }
    )
}

class GetChangesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(getChangesRequest, GetChangesRequest, { this.context })
        checkProtoBufConversion(getChangesMaxRequest, GetChangesRequest, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(getChangesRequest, GetChangesRequest, { this.context })
        checkJsonConversion(getChangesMaxRequest, GetChangesRequest, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(getChangesRequest, GetChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        keys: [uBu6L+ARRCgpUuyks8f73g, CXTD69pnTdsytwq0yxPryA]
        toVersion: 3456
        filterSoftDeleted: true
        fromVersion: 1234

        """.trimIndent()

        checkYamlConversion(getChangesMaxRequest, GetChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        keys: [uBu6L+ARRCgpUuyks8f73g, CXTD69pnTdsytwq0yxPryA]
        select:
        - value
        filter: !Exists value
        order: value
        toVersion: 3456
        filterSoftDeleted: true
        fromVersion: 1234

        """.trimIndent()
    }
}
