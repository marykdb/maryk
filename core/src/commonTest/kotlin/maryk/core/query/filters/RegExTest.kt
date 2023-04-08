package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class RegExTest {
    private val regEx = RegEx(
        TestMarykModel { string::ref } with Regex(".*")
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name toUnitLambda { TestMarykModel }
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
        expect(
            """
            string: .*

            """.trimIndent()
        ) {
            checkYamlConversion(this.regEx, RegEx, { this.context })
        }
    }
}
