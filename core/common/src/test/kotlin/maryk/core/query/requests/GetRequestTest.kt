@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.query.requests

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.RequestContext
import maryk.core.query.descending
import maryk.core.query.filters.Exists
import maryk.core.yaml.MarykYamlReaders
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykModel.key("dR9gVdRcSPw2molM1AiOng")
private val key2 = SimpleMarykModel.key("Vc4WgX/mQHYCSEoLtfLSUQ")

val getRequest = SimpleMarykModel.get(
    key1,
    key2
)

internal val getMaxRequest = SimpleMarykModel.run {
    get(
        key1,
        key2,
        filter = Exists(ref { value }),
        order = ref { value }.descending(),
        toVersion = 333uL,
        filterSoftDeleted = true,
        select = props {
            RootPropRefGraph<SimpleMarykModel>(
                value
            )
        }
    )
}

class GetRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
    ))

    @Test
    fun createAsMap(){
        GetRequest.map(context) {
            mapNonNulls(
                dataModel with SimpleMarykModel,
                keys with listOf(key1, key2)
            )
        }.toDataObject() shouldBe getRequest
    }

    @Test
    fun createAsMaxMap(){
        GetRequest.map(context) {
            mapNonNulls(
                dataModel with SimpleMarykModel,
                keys with listOf(key1, key2),
                filter with Exists(SimpleMarykModel.ref { value }),
                order with SimpleMarykModel.ref { value }.descending(),
                toVersion with 333uL,
                filterSoftDeleted with true,
                select with SimpleMarykModel.props {
                    RootPropRefGraph<SimpleMarykModel>(
                        value
                    )
                }
            )
        }.toDataObject() shouldBe getMaxRequest
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(getRequest, GetRequest, { this.context })
        checkProtoBufConversion(getMaxRequest, GetRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(getRequest, GetRequest, { this.context })
        checkJsonConversion(getMaxRequest, GetRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(getRequest, GetRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
        filterSoftDeleted: true

        """.trimIndent()

        checkYamlConversion(getMaxRequest, GetRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
        select:
        - value
        filter: !Exists value
        order: !Desc value
        toVersion: 333
        filterSoftDeleted: true

        """.trimIndent()
    }

    @Test
    fun convertBasicDefinitionFromYAML() {
        val simpleYaml = """
        dataModel: SimpleMarykModel
        keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
        filter:
        select:
        - value

        """.trimIndent()

        var index = 0

        val reader = MarykYamlReaders {
            simpleYaml[index++].also {
                if (it == '\u0000') {
                    throw Throwable("0 char encountered")
                }
            }
        }

        GetRequest.readJson(reader, this.context)
            .toDataObject()
            .apply {
                dataModel shouldBe SimpleMarykModel
                filter shouldBe null
            }
    }
}
