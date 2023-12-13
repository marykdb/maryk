package maryk.core.query.responses.updates

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.models.key
import maryk.core.properties.types.Bytes
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

internal class OrderedKeysUpdateTest {
    private val key1 = SimpleMarykModel.key("0ruQCs38S2QaByYof-IJgA")
    private val key2 = SimpleMarykModel.key("dR9gVdRcSPw2molM1AiOng")
    private val key3 = SimpleMarykModel.key("Vc4WgX_mQHYCSEoLtfLSUQ")

    private val orderedKeysUpdate = OrderedKeysUpdate(
        keys = listOf(key1, key2, key3),
        version = 1234uL,
        sortingKeys = listOf(Bytes(byteArrayOf(0, 1)), Bytes(byteArrayOf(1, 2)), Bytes(byteArrayOf(2, 3, 4)))
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.Meta.name toUnitLambda { SimpleMarykModel }
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
            keys: [0ruQCs38S2QaByYof-IJgA, dR9gVdRcSPw2molM1AiOng, Vc4WgX_mQHYCSEoLtfLSUQ]
            version: 1234
            sortingKeys: [AAE, AQI, AgME]
            
            """.trimIndent()
        ) {
            checkYamlConversion(this.orderedKeysUpdate, OrderedKeysUpdate, { this.context })
        }
    }
}
