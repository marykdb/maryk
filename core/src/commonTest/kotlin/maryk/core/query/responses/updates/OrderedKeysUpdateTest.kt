package maryk.core.query.responses.updates

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

internal class OrderedKeysUpdateTest {
    private val key1 = SimpleMarykModel.key("0ruQCs38S2QaByYof+IJgA")
    private val key2 = SimpleMarykModel.key("dR9gVdRcSPw2molM1AiOng")
    private val key3 = SimpleMarykModel.key("Vc4WgX/mQHYCSEoLtfLSUQ")

    private val orderedKeysUpdate = OrderedKeysUpdate(
        keys = listOf(key1, key2, key3),
        version = 1234uL
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.orderedKeysUpdate, OrderedKeysUpdate, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.orderedKeysUpdate, OrderedKeysUpdate, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            keys: [0ruQCs38S2QaByYof+IJgA, dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
            version: 1234
            
            """.trimIndent()
        ) {
            checkYamlConversion(this.orderedKeysUpdate, OrderedKeysUpdate, { this.context })
        }
    }
}
