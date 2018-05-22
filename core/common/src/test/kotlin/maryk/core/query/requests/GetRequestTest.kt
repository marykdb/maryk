package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.descending
import maryk.core.query.filters.exists
import maryk.core.yaml.MarykYamlReader
import maryk.test.shouldBe
import kotlin.test.Test

class GetRequestTest {
    private val key1 = SimpleMarykObject.key("dR9gVdRcSPw2molM1AiOng")
    private val key2 = SimpleMarykObject.key("Vc4WgX/mQHYCSEoLtfLSUQ")

    private val getRequest = SimpleMarykObject.get(
        key1,
        key2
    )

    private val getMaxRequest = SimpleMarykObject.run {
        get(
            key1,
            key2,
            filter = ref { value }.exists(),
            order = ref { value }.descending(),
            toVersion = 333L.toUInt64(),
            filterSoftDeleted = true
        )
    }

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.getRequest, GetRequest, this.context)
        checkProtoBufConversion(this.getMaxRequest, GetRequest, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.getRequest, GetRequest, this.context)
        checkJsonConversion(this.getMaxRequest, GetRequest, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.getRequest, GetRequest, this.context) shouldBe """
        dataModel: SimpleMarykObject
        keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
        filterSoftDeleted: true

        """.trimIndent()

        checkYamlConversion(this.getMaxRequest, GetRequest, this.context) shouldBe """
        dataModel: SimpleMarykObject
        keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
        filter: !Exists value
        order: !Desc value
        toVersion: 0x000000000000014d
        filterSoftDeleted: true

        """.trimIndent()
    }

    @Test
    fun convert_basic_definition_from_YAML() {
        val simpleYaml = """
        dataModel: SimpleMarykObject
        keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
        filter:

        """.trimIndent()

        var index = 0

        val reader = MarykYamlReader {
            simpleYaml[index++].also {
                if (it == '\u0000') {
                    throw Throwable("0 char encountered")
                }
            }
        }

        GetRequest.readJsonToObject(reader, this.context).apply {
            dataModel shouldBe SimpleMarykObject
            filter shouldBe null
        }
    }
}
