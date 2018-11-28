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

class RegExTest {
    private val regEx = RegEx(
        TestMarykModel.ref { string } with Regex(".*")
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.regEx, RegEx, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.regEx, RegEx, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.regEx, RegEx, { this.context }) shouldBe """
        string: .*

        """.trimIndent()
    }
}
