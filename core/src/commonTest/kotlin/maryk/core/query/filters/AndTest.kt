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

class AndTest {
    private val and = And(
        Exists(SimpleMarykModel.ref { value }),
        Equals(
            SimpleMarykModel.ref { value } with "hoi"
        )
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.and, And, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.and, And, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.and, And, { this.context }) shouldBe """
        - !Exists value
        - !Equals
          value: hoi

        """.trimIndent()
    }
}
