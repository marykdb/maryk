package maryk.core.query.responses.updates

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

internal class AdditionUpdateTest {
    private val values = SimpleMarykModel(
        "nice value"
    )

    private val key = SimpleMarykModel.key("0ruQCs38S2QaByYof+IJgA")

    private val additionUpdate = AdditionUpdate(
        key = key,
        version = 1uL,
        insertionIndex = 5,
        values = values
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.additionUpdate, AdditionUpdate, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.additionUpdate, AdditionUpdate, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            key: 0ruQCs38S2QaByYof+IJgA
            version: 1
            insertionIndex: 5
            values:
              value: nice value

            """.trimIndent()
        ) {
            checkYamlConversion(this.additionUpdate, AdditionUpdate, { this.context })
        }
    }
}
