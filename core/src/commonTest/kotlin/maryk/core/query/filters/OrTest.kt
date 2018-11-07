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
        Exists(SimpleMarykModel.ref { value }),
        Equals(SimpleMarykModel.ref { value } with "hoi")
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.or, Or, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.or, Or, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.or, Or, { this.context }) shouldBe """
        - !Exists value
        - !Equals
          value: hoi

        """.trimIndent()
    }
}
