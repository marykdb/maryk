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
        TestMarykModel.ref { string } with ".*"
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.regEx, RegEx, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.regEx, RegEx, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.regEx, RegEx, { this.context }) shouldBe """
        string: .*

        """.trimIndent()
    }
}
