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

class PrefixTest {
    private val prefix = Prefix(
        TestMarykModel.ref { string } with "te"
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.prefix, Prefix, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.prefix, Prefix, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.prefix, Prefix, { this.context }) shouldBe """
        string: te

        """.trimIndent()
    }
}
