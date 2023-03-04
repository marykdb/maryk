package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.ValueRange
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class RangeTest {
    private val range = Range(
        TestMarykModel { string::ref } with ValueRange(
            from = "test",
            to = "test999",
            inclusiveFrom = false,
            inclusiveTo = false
        ),
        TestMarykModel { int::ref } with 3..5
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel.Model
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.range, Range.Model, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.range, Range.Model, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string: [!Exclude test, !Exclude test999]
            int: [3, 5]

            """.trimIndent()
        ) {
            checkYamlConversion(this.range, Range.Model, { this.context })
        }
    }
}
