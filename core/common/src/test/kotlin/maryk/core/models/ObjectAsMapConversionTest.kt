package maryk.core.models

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufObjectValuesConversion
import maryk.checkYamlConversion
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.RequestContext
import maryk.core.query.descending
import maryk.core.query.filters.Exists
import maryk.core.query.requests.GetRequest
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykModel.key("dR9gVdRcSPw2molM1AiOng")
private val key2 = SimpleMarykModel.key("Vc4WgX/mQHYCSEoLtfLSUQ")

private val context = RequestContext(mapOf(
    SimpleMarykModel.name to { SimpleMarykModel }
))

class InjectInRequestTest {
    private val getRequestWithInjectable = GetRequest.map(context) {
        mapNonNulls(
            dataModel with SimpleMarykModel,
            keys with listOf(key1, key2),
            filter with Exists(SimpleMarykModel.ref { value }),
            order with SimpleMarykModel.ref { value }.descending(),
            toVersion with 333L.toUInt64(),
            filterSoftDeleted with true,
            select with SimpleMarykModel.props {
                RootPropRefGraph<SimpleMarykModel>(
                    value
                )
            }
        )
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(
            getRequestWithInjectable,
            GetRequest,
            { context },
            checker = { a, b ->
                a.toDataObject() shouldBe b.toDataObject()
            }
        ) shouldBe """
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
    fun convertToJSONAndBack() {
        checkJsonConversion(
            getRequestWithInjectable,
            GetRequest,
            { context },
            checker = { a, b ->
                a.toDataObject() shouldBe b.toDataObject()
            }
        )
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufObjectValuesConversion(
            getRequestWithInjectable,
            GetRequest,
            { context },
            checker = { a, b ->
                a.toDataObject() shouldBe b.toDataObject()
            }
        )
    }
}
