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

class LessThanEqualsTest {
    private val lessThanEquals = LessThanEquals(
        TestMarykModel.ref { string } with "test",
        TestMarykModel.ref { int } with 6
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.lessThanEquals, LessThanEquals, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.lessThanEquals, LessThanEquals, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.lessThanEquals, LessThanEquals, { this.context }) shouldBe """
        string: test
        int: 6

        """.trimIndent()
    }
}
