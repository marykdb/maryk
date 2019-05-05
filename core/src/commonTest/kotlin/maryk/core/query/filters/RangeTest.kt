package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.ValueRange
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

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
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.range, Range, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.range, Range, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.range, Range, { this.context }) shouldBe """
        string: [!Exclude test, !Exclude test999]
        int: [3, 5]

        """.trimIndent()
    }
}
