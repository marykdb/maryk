package maryk.core.query.requests

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.descending
import maryk.core.query.filters.Exists
import maryk.core.yaml.MarykYamlReader
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykModel.key("dR9gVdRcSPw2molM1AiOng")
private val key2 = SimpleMarykModel.key("Vc4WgX/mQHYCSEoLtfLSUQ")

internal val getSelectRequest = SimpleMarykModel.getSelect(
    key1,
    key2,
    select = SimpleMarykModel.props {
        RootPropRefGraph<SimpleMarykModel>(
            value
        )
    }
)

internal val getMaxSelectRequest = SimpleMarykModel.run {
    getSelect(
        key1,
        key2,
        filter = Exists(ref { value }),
        order = ref { value }.descending(),
        toVersion = 333L.toUInt64(),
        filterSoftDeleted = true,
        select = props {
            RootPropRefGraph<SimpleMarykModel>(
                value
            )
        }
    )
}

class GetSelectRequestTest {
    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(getSelectRequest, GetSelectRequest, { this.context })
        checkProtoBufConversion(getMaxSelectRequest, GetSelectRequest, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(getSelectRequest, GetSelectRequest, { this.context })
        checkJsonConversion(getMaxSelectRequest, GetSelectRequest, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(getSelectRequest, GetSelectRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
        filterSoftDeleted: true
        select:
        - value

        """.trimIndent()

        checkYamlConversion(getMaxSelectRequest, GetSelectRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
        filter: !Exists value
        order: !Desc value
        toVersion: 333
        filterSoftDeleted: true
        select:
        - value

        """.trimIndent()
    }

    @Test
    fun convert_basic_definition_from_YAML() {
        val simpleYaml = """
        dataModel: SimpleMarykModel
        keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
        filter:
        select:
        - value

        """.trimIndent()

        var index = 0

        val reader = MarykYamlReader {
            simpleYaml[index++].also {
                if (it == '\u0000') {
                    throw Throwable("0 char encountered")
                }
            }
        }

        GetSelectRequest.readJson(reader, this.context)
            .toDataObject()
            .apply {
                dataModel shouldBe SimpleMarykModel
                filter shouldBe null
            }
    }
}
