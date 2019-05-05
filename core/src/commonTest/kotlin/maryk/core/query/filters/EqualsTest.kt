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

class EqualsTest {
    private val equals = Equals(
        TestMarykModel { string::ref } with "test",
        TestMarykModel { int::ref } with 5
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.name toUnitLambda { TestMarykModel }
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.equals, Equals, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.equals, Equals, { this.context }) shouldBe """
        {
        	"string": "test",
        	"int": 5
        }
        """.trimIndent()
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(this.equals, Equals, { this.context }) shouldBe """
        string: test
        int: 5

        """.trimIndent()
    }
}
