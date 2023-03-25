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

class PrefixTest {
    private val prefix = Prefix(
        TestMarykModel { string::ref } with "te"
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
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
        expect(
            """
            string: te

            """.trimIndent()
        ) {
            checkYamlConversion(this.prefix, Prefix, { this.context })
        }
    }
}
