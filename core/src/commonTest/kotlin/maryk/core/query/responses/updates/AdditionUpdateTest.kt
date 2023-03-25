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
    private val values = SimpleMarykModel.run { create(
        value with "nice value"
    ) }

    private val key = SimpleMarykModel.key("0ruQCs38S2QaByYof+IJgA")

    private val additionUpdate = AdditionUpdate(
        key = key,
        version = 1uL,
        firstVersion = 0uL,
        insertionIndex = 5,
        values = values,
        isDeleted = false
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel }
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
            firstVersion: 0
            insertionIndex: 5
            isDeleted: false
            values:
              value: nice value

            """.trimIndent()
        ) {
            checkYamlConversion(this.additionUpdate, AdditionUpdate, { this.context })
        }
    }
}
