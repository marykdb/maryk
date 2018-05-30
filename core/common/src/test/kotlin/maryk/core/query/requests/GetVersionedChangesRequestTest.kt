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

private val key1 = SimpleMarykObject.key("WWurg6ysTsozoMei/SurOw")
private val key2 = SimpleMarykObject.key("awfbjYrVQ+cdXblfQKV10A")

internal val getVersionedChangesRequest = SimpleMarykObject.getVersionedChanges(
    key1,
    key2,
    fromVersion = 1234L.toUInt64()
)

internal val getVersionedChangesMaxRequest = SimpleMarykObject.run {
    getVersionedChanges(
        key1,
        key2,
        filter = Exists(ref { value }),
        order = ref { value }.descending(),
        fromVersion = 1234L.toUInt64(),
        toVersion = 12345L.toUInt64(),
        maxVersions = 5.toUInt32(),
        filterSoftDeleted = true
    )
}

class GetVersionedChangesRequestTest {
    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to { SimpleMarykObject }
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(getVersionedChangesRequest, GetVersionedChangesRequest, { this.context })
        checkProtoBufConversion(getVersionedChangesMaxRequest, GetVersionedChangesRequest, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(getVersionedChangesRequest, GetVersionedChangesRequest, { this.context })
        checkJsonConversion(getVersionedChangesMaxRequest, GetVersionedChangesRequest, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(getVersionedChangesRequest, GetVersionedChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykObject
        keys: [WWurg6ysTsozoMei/SurOw, awfbjYrVQ+cdXblfQKV10A]
        filterSoftDeleted: true
        fromVersion: 0x00000000000004d2
        maxVersions: 1000

        """.trimIndent()

        checkYamlConversion(getVersionedChangesMaxRequest, GetVersionedChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykObject
        keys: [WWurg6ysTsozoMei/SurOw, awfbjYrVQ+cdXblfQKV10A]
        filter: !Exists value
        order: !Desc value
        toVersion: 0x0000000000003039
        filterSoftDeleted: true
        fromVersion: 0x00000000000004d2
        maxVersions: 5

        """.trimIndent()
    }
}
