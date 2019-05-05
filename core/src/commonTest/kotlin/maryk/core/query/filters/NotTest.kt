package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class NotTest {
    private val not = Not(
        Exists(SimpleMarykModel { value::ref })
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.not, Not, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.not, Not, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.not, Not, { this.context }) shouldBe """
        - !Exists value

        """.trimIndent()
    }
}
