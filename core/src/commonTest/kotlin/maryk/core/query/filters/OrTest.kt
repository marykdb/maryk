package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.SimpleMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class OrTest {
    private val or = Or(
        Exists(SimpleMarykModel { value::ref }),
        Equals(SimpleMarykModel { value::ref } with "hoi")
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.or, Or, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.or, Or, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.or, Or, { this.context }) shouldBe """
        - !Exists value
        - !Equals
          value: hoi

        """.trimIndent()
    }
}
