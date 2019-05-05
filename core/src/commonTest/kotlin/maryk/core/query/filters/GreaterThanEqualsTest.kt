package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class GreaterThanEqualsTest {
    private val greaterThanEquals = GreaterThanEquals(
        TestMarykModel { string::ref } with "test",
        TestMarykModel { int::ref } with 6
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.greaterThanEquals, GreaterThanEquals, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.greaterThanEquals, GreaterThanEquals, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.greaterThanEquals, GreaterThanEquals, { this.context }) shouldBe """
        string: test
        int: 6

        """.trimIndent()
    }
}
