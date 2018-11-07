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

class CheckTest {
    private val valueCheck = Check(
        TestMarykModel.ref { string } with "test",
        TestMarykModel.ref { int } with 42
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.valueCheck, Check, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.valueCheck, Check, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.valueCheck, Check, { this.context }) shouldBe """
        string: test
        int: 42

        """.trimIndent()
    }
}
