package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class ChangeTest {
    private val valueChange = Change(
        TestMarykModel.ref { string } with "test",
        TestMarykModel.ref { int } with 5
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun testValueChange() {
        valueChange.referenceValuePairs[0].reference shouldBe TestMarykModel.ref { string }
        valueChange.referenceValuePairs[0].value shouldBe "test"
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.valueChange, Change, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.valueChange, Change, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.valueChange, Change, { this.context }) shouldBe """
        string: test
        int: 5

        """.trimIndent()
    }
}
